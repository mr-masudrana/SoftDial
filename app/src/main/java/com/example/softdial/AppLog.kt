package com.example.softdial

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small log file kept on the phone (viewable in Settings -> App logs). */
object AppLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private const val MAX_BYTES = 200_000L

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.applicationContext.filesDir, "app.log")
    }

    @Synchronized
    fun d(tag: String, msg: String) {
        val f = file ?: return
        try {
            if (f.exists() && f.length() > MAX_BYTES) {
                val lines = f.readLines()
                f.writeText(lines.takeLast(lines.size / 2).joinToString("\n") + "\n")
            }
            f.appendText("${fmt.format(Date())}  [$tag] $msg\n")
        } catch (e: Exception) { /* logging must never crash the app */ }
    }

    @Synchronized
    fun read(maxLines: Int = 300): String {
        val f = file ?: return ""
        if (!f.exists()) return ""
        return try { f.readLines().takeLast(maxLines).joinToString("\n") } catch (e: Exception) { "" }
    }

    @Synchronized
    fun count(): Int {
        val f = file ?: return 0
        if (!f.exists()) return 0
        return try { f.readLines().size } catch (e: Exception) { 0 }
    }

    @Synchronized
    fun clear() {
        try { file?.writeText("") } catch (e: Exception) { }
    }

    /** 01712345678 -> 017***78 (logs can be shared, so numbers are partly hidden). */
    fun mask(n: String?): String {
        val s = n ?: return "?"
        return if (s.length > 5) s.take(3) + "***" + s.takeLast(2) else s
    }
}
