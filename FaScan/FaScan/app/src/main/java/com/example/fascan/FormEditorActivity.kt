package com.example.fascan

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class FormEditorActivity : AppCompatActivity() {

    companion object { const val EXTRA_FORM = "form_name" }

    private var originalName: String? = null
    private lateinit var etName: EditText
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_editor)

        etName = findViewById(R.id.etFormName)
        container = findViewById(R.id.questionContainer)
        originalName = intent.getStringExtra(EXTRA_FORM)

        val existing = originalName?.let { n -> Store.getForms(this).firstOrNull { it.name == n } }
        if (existing != null) {
            etName.setText(existing.name)
            existing.questions.forEach { addQuestionRow(it) }
        } else {
            addQuestionRow(null)
        }
        findViewById<TextView>(R.id.tvEditorTitle).text = if (existing != null) "Edit form" else "New form"

        findViewById<Button>(R.id.btnAddQuestion).setOnClickListener {
            if (container.childCount >= FormLayout.MAX_Q) {
                toast("A page holds at most ${FormLayout.MAX_Q} questions")
            } else addQuestionRow(null)
        }
        findViewById<Button>(R.id.btnSaveForm).setOnClickListener { save() }
        findViewById<Button>(R.id.btnPreview).setOnClickListener { collect()?.let { showPreview(it) } }
        findViewById<Button>(R.id.btnSharePdf).setOnClickListener { collect()?.let { sharePdf(it) } }
    }

    private fun addQuestionRow(q: Question?) {
        val row = layoutInflater.inflate(R.layout.item_question, container, false)
        if (q != null) {
            row.findViewById<EditText>(R.id.etQuestion).setText(q.text)
            row.findViewById<EditText>(R.id.etOptions).setText(q.options.joinToString(", "))
        }
        row.findViewById<Button>(R.id.btnRemove).setOnClickListener {
            container.removeView(row)
            renumber()
        }
        container.addView(row)
        renumber()
    }

    private fun renumber() {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).findViewById<TextView>(R.id.tvNum).text = "Question ${i + 1}"
        }
    }

    /** Reads the screen into a FormTemplate, or shows an error and returns null. */
    private fun collect(): FormTemplate? {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) { etName.error = "Form name is required"; return null }

        val questions = ArrayList<Question>()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val text = row.findViewById<EditText>(R.id.etQuestion).text.toString().trim()
            val options = row.findViewById<EditText>(R.id.etOptions).text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (text.isEmpty()) { toast("Question ${i + 1} has no text"); return null }
            if (options.size < 2 || options.size > FormLayout.MAX_OPT) {
                toast("Question ${i + 1} needs 2 to ${FormLayout.MAX_OPT} options separated by commas")
                return null
            }
            questions.add(Question(text, options))
        }
        if (questions.isEmpty()) { toast("Add at least one question"); return null }
        return FormTemplate(name, questions)
    }

    private fun save() {
        val t = collect() ?: return
        if (!Store.saveForm(this, originalName, t)) {
            etName.error = "A form with this name already exists"
            return
        }
        Store.addHistory(this, if (originalName == null) "Created form \"${t.name}\"" else "Edited form \"${t.name}\"")
        toast("Form saved")
        finish()
    }

    private fun showPreview(t: FormTemplate) {
        val bmp = Bitmap.createBitmap(FormLayout.PAGE_W, FormLayout.PAGE_H, Bitmap.Config.ARGB_8888)
        FormRenderer.draw(Canvas(bmp), 1f, t, null)
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            setPadding(16, 16, 16, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Form preview")
            .setView(ScrollView(this).apply { addView(iv) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun sharePdf(t: FormTemplate) {
        try {
            val file = FormRenderer.createPdf(this, t)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Store.addHistory(this, "Exported PDF of \"${t.name}\"")
            startActivity(Intent.createChooser(intent, "Share or print form"))
        } catch (e: Exception) {
            toast("Could not create PDF: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
