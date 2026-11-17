package com.example.fascan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

/** For every question: the option indexes that were found shaded. */
class OmrResult(val aligned: Boolean, val picks: List<List<Int>>)

object OmrScanner {
    private const val WORK_SIZE = 1400     // longest side used for processing
    private const val OUT_SCALE = 2f       // aligned page = 2 pixels per point
    private const val FILL_RATIO = 0.45f   // share of dark pixels needed to call a circle "shaded"

    private val DX = intArrayOf(1, -1, 0, 0)
    private val DY = intArrayOf(0, 0, 1, -1)

    fun scale(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= WORK_SIZE) return src
        val f = WORK_SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
    }

    fun toGray(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }
        return px
    }

    /** Otsu's method: picks the gray level that best separates "ink" from "paper". */
    fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        for (g in gray) hist[g.coerceIn(0, 255)]++
        val total = gray.size
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0
        var best = 0.0
        var thr = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        return thr
    }

    /** Finds the 4 black corner squares. Returns x,y of TL, TR, BR, BL or null if any is missing. */
    fun findMarkers(gray: IntArray, w: Int, h: Int, thr: Int): FloatArray? {
        val rw = (w * 0.38f).toInt()
        val rh = (h * 0.38f).toInt()
        val origins = arrayOf(
            intArrayOf(0, 0), intArrayOf(w - rw, 0),
            intArrayOf(w - rw, h - rh), intArrayOf(0, h - rh)
        )
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val p = bestBlob(gray, w, h, thr, origins[i][0], origins[i][1], rw, rh) ?: return null
            out[2 * i] = p.x
            out[2 * i + 1] = p.y
        }
        return out
    }

    /** Largest solid, roughly square dark blob inside the given region (flood fill). */
    private fun bestBlob(gray: IntArray, w: Int, h: Int, thr: Int,
                         x0: Int, y0: Int, rw: Int, rh: Int): PointF? {
        val visited = BooleanArray(rw * rh)
        val queue = IntArray(rw * rh)
        val minArea = (0.0007 * w * h).toInt()
        val maxArea = (0.02 * w * h).toInt()
        var best: PointF? = null
        var bestArea = 0

        for (sy in 0 until rh) for (sx in 0 until rw) {
            val si = sy * rw + sx
            if (visited[si]) continue
            visited[si] = true
            if (gray[(y0 + sy) * w + (x0 + sx)] > thr) continue

            var head = 0
            var tail = 0
            queue[tail++] = si
            var area = 0
            var minX = sx; var maxX = sx; var minY = sy; var maxY = sy
            var sumX = 0L; var sumY = 0L

            while (head < tail) {
                val cur = queue[head++]
                val cx = cur % rw
                val cy = cur / rw
                area++; sumX += cx; sumY += cy
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                for (d in 0 until 4) {
                    val nx = cx + DX[d]
                    val ny = cy + DY[d]
                    if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue
                    val ni = ny * rw + nx
                    if (visited[ni]) continue
                    visited[ni] = true
                    if (gray[(y0 + ny) * w + (x0 + nx)] <= thr) queue[tail++] = ni
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val ratio = bw.toFloat() / bh
            val solidity = area.toFloat() / (bw * bh)
            if (area in minArea..maxArea && ratio in 0.6f..1.6f && solidity >= 0.65f && area > bestArea) {
                bestArea = area
                best = PointF(x0 + sumX.toFloat() / area, y0 + sumY.toFloat() / area)
            }
        }
        return best
    }

    /** Straightens the photo so the page is exactly PAGE_W x PAGE_H (x2). Handles tilt and perspective. */
    fun align(src: Bitmap, found: FloatArray): Bitmap {
        val target = FormLayout.markerCenters()
        val dst = FloatArray(8) { target[it] * OUT_SCALE }
        val m = Matrix()
        m.setPolyToPoly(found, 0, dst, 0, 4)
        val out = Bitmap.createBitmap(
            (FormLayout.PAGE_W * OUT_SCALE).toInt(),
            (FormLayout.PAGE_H * OUT_SCALE).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Looks at every circle on the aligned page and reports which ones are shaded. */
    fun readAnswers(aligned: Bitmap, form: FormTemplate): List<List<Int>> {
        val w = aligned.width
        val h = aligned.height
        val gray = toGray(aligned)
        val thr = otsu(gray)
        val r = (5.5f * OUT_SCALE).toInt()   // sample only the inside, so the printed ring is ignored

        val result = ArrayList<List<Int>>()
        form.questions.take(FormLayout.MAX_Q).forEachIndexed { qi, q ->
            val marked = ArrayList<Int>()
            for (oi in 0 until minOf(q.options.size, FormLayout.MAX_OPT)) {
                val c = FormLayout.bubbleCenter(qi, oi)
                val cx = (c.x * OUT_SCALE).toInt()
                val cy = (c.y * OUT_SCALE).toInt()
                var dark = 0
                var total = 0
                for (dy in -r..r) for (dx in -r..r) {
                    if (dx * dx + dy * dy > r * r) continue
                    val x = cx + dx
                    val y = cy + dy
                    if (x < 0 || y < 0 || x >= w || y >= h) continue
                    total++
                    if (gray[y * w + x] <= thr) dark++
                }
                if (total > 0 && dark.toFloat() / total >= FILL_RATIO) marked.add(oi)
            }
            result.add(marked)
        }
        return result
    }

    fun status(form: FormTemplate, r: OmrResult): String {
        if (!r.aligned) return STATUS_UNREADABLE
        val total = minOf(form.questions.size, FormLayout.MAX_Q)
        return if (r.picks.size == total && r.picks.all { it.size == 1 }) STATUS_SUCCESS else STATUS_REVIEW
    }

    fun summary(form: FormTemplate, r: OmrResult): String = buildString {
        form.questions.take(FormLayout.MAX_Q).forEachIndexed { i, q ->
            val p = r.picks.getOrNull(i) ?: emptyList()
            val answer = when {
                p.isEmpty() -> "(no answer detected)"
                p.size == 1 -> q.options[p[0]]
                else -> "multiple marks: " + p.joinToString { q.options[it] }
            }
            append("${i + 1}. ${q.text}: $answer\n")
        }
    }.trim()
}
