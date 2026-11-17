package com.example.fascan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var loadingScreen: View
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvPercent: TextView
    private lateinit var tvStep: TextView
    private lateinit var spForm: Spinner
    private var forms: List<FormTemplate> = emptyList()
    private var imageCapture: ImageCapture? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forms = Store.getForms(this)
        if (forms.isEmpty()) {
            Toast.makeText(this, "Create a form first in the Forms tab", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        loadingScreen = findViewById(R.id.loadingScreen)
        pbLoading = findViewById(R.id.pbLoading)
        tvPercent = findViewById(R.id.tvPercent)
        tvStep = findViewById(R.id.tvStep)
        spForm = findViewById(R.id.spForm)

        spForm.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, forms.map { it.name })

        findViewById<Button>(R.id.btnCapture).setOnClickListener { capturePhoto() }
        findViewById<Button>(R.id.btnSample).setOnClickListener { process(sampleBitmap(selectedForm())) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun selectedForm(): FormTemplate = forms[spForm.selectedItemPosition.coerceAtLeast(0)]

    // ---------- camera ----------
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        showLoading(true, 0, "Capturing photo...")
        capture.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val raw = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close() // always close, or the camera stops delivering frames
                    val m = Matrix().apply { postRotate(rotation.toFloat()) }
                    process(Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true))
                }

                override fun onError(exception: ImageCaptureException) {
                    showLoading(false, 0, "")
                    Toast.makeText(this@ScannerActivity, "Capture failed: ${exception.message}",
                        Toast.LENGTH_LONG).show()
                }
            })
    }

    // ---------- loading screen ----------
    private fun showLoading(show: Boolean, percent: Int, step: String) {
        loadingScreen.visibility = if (show) View.VISIBLE else View.GONE
        setProgress(percent, step)
    }

    private fun setProgress(percent: Int, step: String) {
        pbLoading.progress = percent
        tvPercent.text = "$percent%"
        tvStep.text = step
    }

    /** Called from the worker thread: pause briefly so each step is visible, then update the UI. */
    private fun step(percent: Int, text: String) {
        Thread.sleep(250)
        runOnUiThread { setProgress(percent, text) }
    }

    // ---------- processing pipeline ----------
    private fun process(photo: Bitmap) {
        val form = selectedForm()
        showLoading(true, 5, "Preparing image...")

        // heavy pixel work must NOT run on the main thread, or the loading screen would freeze
        Thread {
            try {
                val work = OmrScanner.scale(photo)
                step(20, "Preparing image...")
                val gray = OmrScanner.toGray(work)
                val thr = OmrScanner.otsu(gray)

                step(40, "Locating the 4 corner squares...")
                val corners = OmrScanner.findMarkers(gray, work.width, work.height, thr)

                var picks: List<List<Int>> = emptyList()
                if (corners != null) {
                    step(60, "Aligning the form...")
                    val aligned = OmrScanner.align(work, corners)
                    step(80, "Detecting shaded circles...")
                    picks = OmrScanner.readAnswers(aligned, form)
                }
                val omr = OmrResult(corners != null, picks)
                runOnUiThread { runOcr(work, form, omr) }
            } catch (e: Throwable) {
                runOnUiThread { fail(e) }
            }
        }.start()
    }

    private fun runOcr(work: Bitmap, form: FormTemplate, omr: OmrResult) {
        if (isFinishing || isDestroyed) return
        setProgress(90, "Reading text (OCR)...")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(work, 0))
            .addOnSuccessListener { showScanResult(form, omr, it.text.trim()) }
            .addOnFailureListener { showScanResult(form, omr, "") } // OMR result is still useful
    }

    private fun fail(e: Throwable) {
        if (isFinishing || isDestroyed) return
        showLoading(false, 0, "")
        Store.addHistory(this, "Scan failed: ${e.message}")
        Toast.makeText(this, "Scan failed: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun showScanResult(form: FormTemplate, omr: OmrResult, ocrText: String) {
        if (isFinishing || isDestroyed) return
        showLoading(true, 100, "Done")
        loadingScreen.visibility = View.GONE

        val status = OmrScanner.status(form, omr)
        val summary = if (omr.aligned) OmrScanner.summary(form, omr)
        else "Could not find all 4 black corner squares. Fit the whole page in view on a flat, light surface with good lighting."
        val words = ocrText.split(Regex("\\s+")).count { it.isNotBlank() }

        AlertDialog.Builder(this)
            .setTitle("Result: $status")
            .setMessage("Form: ${form.name}\n\n$summary\n\nText detected (OCR): $words words")
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ -> save(form, status, summary, ocrText) }
            .setNegativeButton("Retake", null)
            .show()
    }

    private fun save(form: FormTemplate, status: String, summary: String, ocrText: String) {
        val details = summary + if (ocrText.isNotEmpty()) "\n\nOCR text:\n$ocrText" else ""
        Store.addRecord(this, ScanRecord(form.name, details, status, System.currentTimeMillis()))
        Store.addHistory(this, "Scanned \"${form.name}\" -> $status")
        NotificationHelper.show(this, "Scan complete", "${form.name}: $status")
        finish()
    }

    // ---------- built-in test (works on the emulator, no printing needed) ----------
    /** Draws the selected form on a dark "table", slightly rotated, with some circles shaded. */
    private fun sampleBitmap(form: FormTemplate): Bitmap {
        val bmp = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(150, 150, 150))
        val s = 1.5f
        c.save()
        c.translate(500f, 700f)
        c.rotate(3f)
        c.translate(-FormLayout.PAGE_W * s / 2f, -FormLayout.PAGE_H * s / 2f)
        val answers = HashMap<Int, Int>()
        form.questions.take(FormLayout.MAX_Q).forEachIndexed { qi, q ->
            if (qi != 1 || form.questions.size < 3) answers[qi] = qi % minOf(q.options.size, FormLayout.MAX_OPT)
            // question 2 is deliberately left blank so you can see "Needs Review"
        }
        FormRenderer.draw(c, s, form, answers)
        c.restore()
        return bmp
    }
}
