package com.example.fascan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Fixed page geometry (A4 in points). The printer (FormRenderer) and the scanner (OmrScanner)
 * both read these numbers, so a bubble is always where the scanner expects it.
 */
object FormLayout {
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARKER = 36      // size of the black corner squares
    const val MARGIN = 30
    const val LEFT = 50
    const val ROW_TOP = 110
    const val ROW_H = 48
    const val COL_W = 99
    const val BUBBLE_R = 9
    const val MAX_Q = 13
    const val MAX_OPT = 5

    /** Centers of the 4 corner squares: top-left, top-right, bottom-right, bottom-left. */
    fun markerCenters(): FloatArray {
        val half = MARKER / 2f
        return floatArrayOf(
            MARGIN + half, MARGIN + half,
            PAGE_W - MARGIN - half, MARGIN + half,
            PAGE_W - MARGIN - half, PAGE_H - MARGIN - half,
            MARGIN + half, PAGE_H - MARGIN - half
        )
    }

    fun bubbleCenter(q: Int, o: Int) =
        PointF(LEFT + o * COL_W + BUBBLE_R.toFloat(), ROW_TOP + q * ROW_H + 32f)
}

object FormRenderer {

    /** Draws the form. [answers] (question index -> option index) fills circles, used only for the sample test. */
    fun draw(canvas: Canvas, scale: Float, form: FormTemplate, answers: Map<Int, Int>? = null) {
        canvas.save()
        canvas.scale(scale, scale)

        val white = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, FormLayout.PAGE_W.toFloat(), FormLayout.PAGE_H.toFloat(), white)

        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val half = FormLayout.MARKER / 2f
        val c = FormLayout.markerCenters()
        for (i in 0 until 4) {
            canvas.drawRect(c[2 * i] - half, c[2 * i + 1] - half, c[2 * i] + half, c[2 * i + 1] + half, black)
        }

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.2f }

        canvas.drawText(form.name.take(30), FormLayout.LEFT.toFloat(), 82f, title)
        canvas.drawText("Shade the circle completely with a dark pen or pencil.", FormLayout.LEFT.toFloat(), 98f, small)

        form.questions.take(FormLayout.MAX_Q).forEachIndexed { qi, q ->
            val top = FormLayout.ROW_TOP + qi * FormLayout.ROW_H
            canvas.drawText("${qi + 1}. ${q.text.take(75)}", FormLayout.LEFT.toFloat(), top + 14f, text)
            q.options.take(FormLayout.MAX_OPT).forEachIndexed { oi, opt ->
                val p = FormLayout.bubbleCenter(qi, oi)
                canvas.drawCircle(p.x, p.y, FormLayout.BUBBLE_R.toFloat(), ring)
                if (answers?.get(qi) == oi) {
                    canvas.drawCircle(p.x, p.y, FormLayout.BUBBLE_R - 1.5f, black)
                }
                canvas.drawText(opt.take(11), p.x + FormLayout.BUBBLE_R + 5f, p.y + 3f, small)
            }
        }
        canvas.restore()
    }

    /** Writes the printable PDF into the app cache and returns the file. */
    fun createPdf(ctx: Context, form: FormTemplate): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(FormLayout.PAGE_W, FormLayout.PAGE_H, 1).create())
        draw(page.canvas, 1f, form, null)
        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "forms").apply { mkdirs() }
        val file = File(dir, form.name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
