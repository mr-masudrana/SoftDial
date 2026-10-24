package com.example.softdial

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ListView
import android.widget.Toast

/** Makes a ListView look like a column of cards. */
fun styleCardList(list: ListView) {
    val p = (6 * list.resources.displayMetrics.density).toInt()
    list.divider = null
    list.dividerHeight = 0
    list.clipToPadding = false
    list.setPadding(0, p, 0, p)
    list.selector = ColorDrawable(Color.TRANSPARENT)
}

fun copyNumber(ctx: Context, number: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("number", number))
    Toast.makeText(ctx, R.string.number_copied, Toast.LENGTH_SHORT).show()
}

fun fmtDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/** Runs [block] (e.g. swapping the adapter) and restores the scroll position. */
fun ListView.keepScroll(block: () -> Unit) {
    val idx = firstVisiblePosition
    val top = getChildAt(0)?.top ?: 0
    block()
    setSelectionFromTop(idx, top)
}

fun initialOf(title: String): String {
    val c = title.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (c.isDigit()) "#" else c.uppercase()
}

/** Call timer: 05:07 or 1:05:07. */
fun fmtTimer(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    else "%02d:%02d".format(sec / 60, sec % 60)
