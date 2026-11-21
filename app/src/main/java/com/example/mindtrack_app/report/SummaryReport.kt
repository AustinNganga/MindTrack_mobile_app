package com.example.mindtrack_app.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.mindtrack_app.data.CheckIn
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.MoodScreenAnalysis
import com.example.mindtrack_app.data.Moods
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.data.RiskLevel
import com.example.mindtrack_app.data.ScreenTimeRepository
import com.example.mindtrack_app.data.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a printable PDF summary of the last 30 days (mood, screen time, check-ins) entirely on the
 * phone with Android's built-in PdfDocument. The student previews it and decides whether to share it.
 */
object SummaryReport {
    private const val DAYS = 30

    suspend fun create(
        context: Context,
        name: String?,
        checkIns: List<CheckIn>,
        includeNotes: Boolean,
        goalMinutes: Int,
    ): File = withContext(Dispatchers.IO) {
        val days = MoodRepository.lastDays(DAYS)
        val from = days.first()
        val moods = MoodRepository(context).recent(DAYS + 5).filter { it.date >= from }.sortedBy { it.date }
        val screen = ScreenTimeRepository(context).lastDays(DAYS)
        val cutoff = System.currentTimeMillis() - DAYS * 24L * 60 * 60 * 1000
        val recentCheckIns = checkIns.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }

        val doc = PdfDocument()
        val w = Writer(doc)
        w.newPage()

        // ---- header band
        w.canvas.drawRect(0f, 0f, Writer.W.toFloat(), 86f, fill(Color.rgb(79, 75, 196)))
        w.canvas.drawText("MindTrack summary: last $DAYS days", Writer.M, 40f, text(20f, Color.WHITE, bold = true))
        val who = name?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
        w.canvas.drawText(
            "$who${pretty(from)} to ${pretty(days.last())} · created ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}",
            Writer.M, 62f, text(10f, Color.argb(230, 255, 255, 255)),
        )
        w.y = 106f
        w.para(
            "Shared by the student, who chose to create this summary. MindTrack is a student research prototype, not a medical device. " +
                "Nothing here is a diagnosis; it is meant to support a conversation.",
            text(9f, GREY),
        )

        // ---- at a glance
        val avgMood = moods.map { it.mood }.average().takeIf { moods.isNotEmpty() }
        val avgScreen = screen.map { it.totalMillis }.average().takeIf { screen.isNotEmpty() }?.toLong()
        val latest = recentCheckIns.lastOrNull()
        w.ensure(70f)
        val boxW = (Writer.W - 2 * Writer.M - 20f) / 3
        listOf(
            "Mood logged" to "${moods.size} of $DAYS days" + (avgMood?.let { "\naverage: ${Moods.label(Math.round(it).toInt())} (${fmt1(it)}/5)" } ?: ""),
            "Screen time" to (avgScreen?.let { "${formatDuration(it)} a day\naverage over ${screen.size} days" } ?: "not measured\n(usage access off)"),
            "Check-ins" to "${recentCheckIns.size} in $DAYS days" + (latest?.let { "\nlatest: ${it.level.label}" } ?: ""),
        ).forEachIndexed { i, (label, value) ->
            val x = Writer.M + i * (boxW + 10f)
            w.canvas.drawRoundRect(RectF(x, w.y, x + boxW, w.y + 60f), 10f, 10f, fill(Color.rgb(241, 240, 255)))
            w.canvas.drawText(label, x + 10f, w.y + 16f, text(8.5f, GREY, bold = true))
            value.split("\n").forEachIndexed { j, line ->
                w.canvas.drawText(line, x + 10f, w.y + 33f + j * 14f, text(if (j == 0) 11.5f else 9f, if (j == 0) INK else GREY, bold = j == 0))
            }
        }
        w.y += 76f

        // ---- mood
        w.heading("Daily mood")
        if (moods.isEmpty()) {
            w.para("No moods were logged in this period.", text(10f, GREY))
        } else {
            val counts = (5 downTo 1).joinToString("  ·  ") { m -> "${Moods.label(m)}: ${moods.count { it.mood == m }}" }
            w.para(counts, text(9.5f, INK))
            moodChart(w, days, moods.associate { it.date to it.mood })
        }

        // ---- screen time
        w.heading("Screen time (measured by the phone)")
        if (screen.isEmpty()) {
            w.para("Screen time was not available (usage access was not allowed).", text(10f, GREY))
        } else {
            val unlocks = screen.mapNotNull { it.unlocks }
            val lines = mutableListOf("Average ${formatDuration(avgScreen ?: 0)} a day over ${screen.size} recorded days; highest ${formatDuration(screen.maxOf { it.totalMillis })}.")
            if (unlocks.isNotEmpty()) lines += "Average ${Math.round(unlocks.average())} phone unlocks a day."
            if (goalMinutes > 0) {
                val met = screen.count { it.totalMillis <= goalMinutes * 60_000L }
                lines += "Daily goal: ${formatDuration(goalMinutes * 60_000L)}, met on $met of ${screen.size} days."
            }
            lines.forEach { w.para(it, text(9.5f, INK), gap = 1f) }
            screenChart(w, days, screen.associate { it.date to it.totalMillis })
            MoodScreenAnalysis.pattern(moods, screen)?.let { p ->
                w.para(
                    "On higher screen-time days (${formatDuration(p.medianMillis)}+), average mood was ${fmt1(p.moodOnHigherDays)}/5; " +
                        "on lower days ${fmt1(p.moodOnLowerDays)}/5 (${p.pairedDays} days with both). This is a pattern, not proof of cause.",
                    text(9.5f, INK),
                )
            }
        }

        // ---- check-ins
        w.heading("Check-in results")
        if (recentCheckIns.isEmpty()) {
            w.para("No check-ins in this period.", text(10f, GREY))
        } else {
            w.para(
                "Each check-in is a 41-question questionnaire (phone dependence TMD, anxiety STAI). An on-device model trained on " +
                    "1,220 university students gives a Low or Elevated indicator for depressive symptoms (Beck score 11+).",
                text(9f, GREY),
            )
            val cols = floatArrayOf(Writer.M, Writer.M + 150f, Writer.M + 260f)
            w.ensure(18f)
            w.y += 12f
            listOf("Date", "Indicator", "Phone dependence (TMD)").forEachIndexed { i, h -> w.canvas.drawText(h, cols[i], w.y, text(9f, GREY, bold = true)) }
            w.y += 6f
            recentCheckIns.forEach { c ->
                w.ensure(18f)
                w.y += 14f
                w.canvas.drawText(SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(c.timestamp)), cols[0], w.y, text(9.5f, INK))
                w.canvas.drawText(c.level.label, cols[1], w.y, text(9.5f, if (c.level == RiskLevel.ELEVATED) ORANGE else GREEN, bold = true))
                w.canvas.drawText("${QuestionBank.tmdBand(c.tmdTotal)} (${c.tmdTotal}/88)", cols[2], w.y, text(9.5f, INK))
            }
            w.y += 12f
        }

        // ---- notes (only if the student chose to include them)
        if (includeNotes) {
            val notes = moods.filter { it.note.isNotBlank() }
            w.heading("Mood notes (included by the student)")
            if (notes.isEmpty()) w.para("No notes were written in this period.", text(10f, GREY))
            notes.forEach { n -> w.para("${pretty(n.date)} · ${Moods.label(n.mood)}: ${n.note}", text(9.5f, INK), gap = 2f) }
        }

        // ---- support
        w.heading("Support in Kenya")
        w.para("Kenya Red Cross: 1199 (toll-free, 24 hours) · Befrienders Kenya: +254 722 178 177 · or your university counselling centre.", text(9.5f, INK))

        w.finish()
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest summary on the phone
        val file = File(dir, "MindTrack_summary_${MoodRepository.today()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }

    /** Renders each page to an image so the student can check the PDF before sharing it. */
    suspend fun renderPreview(file: File): List<Bitmap> = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
    }

    /** Share sheet (email, WhatsApp, Drive…). The file is shared through a FileProvider, never as a raw path. */
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "My MindTrack summary")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share your MindTrack summary")
    }

    /** Copies the PDF into the phone's Downloads folder (Android 10+, no storage permission needed). */
    suspend fun saveToDownloads(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    // ---------------- charts ----------------

    private fun moodChart(w: Writer, days: List<String>, mood: Map<String, Int>) {
        val h = 96f
        w.ensure(h + 26f)
        val left = Writer.M + 52f
        val right = Writer.W - Writer.M
        val top = w.y + 6f
        val bottom = top + h - 14f
        fun yOf(m: Int) = bottom - (bottom - top) * (m - 1) / 4f
        val slot = (right - left) / days.size
        for (m in 1..5) {
            w.canvas.drawLine(left, yOf(m), right, yOf(m), stroke(Color.rgb(227, 226, 238), 0.8f))
            w.canvas.drawText(Moods.label(m), Writer.M, yOf(m) + 3f, text(7.5f, GREY))
        }
        val pts = days.mapIndexedNotNull { i, d -> mood[d]?.let { (left + i * slot + slot / 2) to yOf(it) } }
        pts.zipWithNext { a, b -> w.canvas.drawLine(a.first, a.second, b.first, b.second, stroke(Color.rgb(79, 75, 196), 1.6f)) }
        pts.forEach { w.canvas.drawCircle(it.first, it.second, 3f, fill(Color.rgb(79, 75, 196))) }
        dayAxis(w, days, left, slot, bottom + 12f)
        w.y = bottom + 26f
    }

    private fun screenChart(w: Writer, days: List<String>, ms: Map<String, Long>) {
        val h = 80f
        w.ensure(h + 30f)
        val left = Writer.M + 52f
        val right = Writer.W - Writer.M
        val top = w.y + 8f
        val bottom = top + h - 14f
        val max = (ms.values.maxOrNull() ?: 0L).coerceAtLeast(60 * 60_000L)
        val slot = (right - left) / days.size
        w.canvas.drawText(formatDuration(max), Writer.M, top + 6f, text(7.5f, GREY))
        w.canvas.drawText("0", Writer.M, bottom, text(7.5f, GREY))
        w.canvas.drawLine(left, bottom, right, bottom, stroke(Color.rgb(227, 226, 238), 0.8f))
        days.forEachIndexed { i, d ->
            val v = ms[d] ?: return@forEachIndexed
            val bh = (bottom - top) * (v.toFloat() / max)
            val x = left + i * slot + slot * 0.2f
            w.canvas.drawRoundRect(RectF(x, bottom - bh, x + slot * 0.6f, bottom), 2f, 2f, fill(Color.rgb(14, 138, 122)))
        }
        dayAxis(w, days, left, slot, bottom + 12f)
        w.y = bottom + 28f
    }

    /** Date labels every 5 days so the axis stays readable. */
    private fun dayAxis(w: Writer, days: List<String>, left: Float, slot: Float, y: Float) {
        days.forEachIndexed { i, d ->
            if (i % 5 == 0 || i == days.lastIndex) w.canvas.drawText(short(d), left + i * slot, y, text(7f, GREY))
        }
    }

    // ---------------- helpers ----------------

    private val INK = Color.rgb(28, 27, 41)
    private val GREY = Color.rgb(94, 93, 114)
    private val GREEN = Color.rgb(31, 157, 122)
    private val ORANGE = Color.rgb(217, 105, 43)

    private fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
    }

    private fun fmt1(x: Double) = String.format(Locale.US, "%.1f", x)
    private fun parse(d: String): Date? = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d) }.getOrNull()
    private fun pretty(d: String) = parse(d)?.let { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(it) } ?: d
    private fun short(d: String) = parse(d)?.let { SimpleDateFormat("d MMM", Locale.getDefault()).format(it) } ?: d

    /** Tiny page manager: A4 pages, a running y position, automatic page breaks and a footer. */
    private class Writer(private val doc: PdfDocument) {
        companion object {
            const val W = 595 // A4 in PostScript points
            const val H = 842
            const val M = 40f
        }

        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        val canvas get() = page!!.canvas
        var y = M

        fun newPage() {
            page?.let { footer(); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }

        fun ensure(height: Float) { if (y + height > H - 50f) newPage() }

        fun finish() {
            page?.let { footer(); doc.finishPage(it) }
            page = null
        }

        private fun footer() {
            canvas.drawText(
                "MindTrack · page $pageNo · Created on the student's phone. Not a diagnosis.",
                M, H - 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = Color.rgb(140, 139, 160) },
            )
        }

        fun heading(title: String) {
            ensure(40f)
            y += 18f
            canvas.drawText(title, M, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f; color = Color.rgb(79, 75, 196); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            y += 8f
        }

        /** Word-wrapped paragraph. */
        fun para(s: String, paint: Paint, gap: Float = 4f) {
            val width = W - 2 * M
            val lines = mutableListOf<String>()
            var line = ""
            s.split(" ").forEach { word ->
                val trial = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(trial) <= width) line = trial else { if (line.isNotEmpty()) lines += line; line = word }
            }
            if (line.isNotEmpty()) lines += line
            lines.forEach {
                ensure(paint.textSize + 4f)
                y += paint.textSize + 3f
                canvas.drawText(it, M, y, paint)
            }
            y += gap
        }
    }
}
