package com.example.softdial

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** Details of a number: contact name, total calls and the last call. */
fun showNumberDetails(ctx: Context, number: String) {
    val contact = Store.contactFor(number)
    val calls = Store.history().filter { Store.same(it.number, number) }
    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    val lines = mutableListOf<String>()
    lines.add(ctx.getString(R.string.det_name) + ": " + (contact?.name ?: ctx.getString(R.string.det_unknown)))
    lines.add(ctx.getString(R.string.det_number) + ": " + number)
    lines.add(ctx.getString(R.string.det_total_calls) + ": " + calls.size)
    calls.firstOrNull()?.let {
        lines.add(ctx.getString(R.string.det_last_call) + ": " + fmt.format(Date(it.time)))
    }
    AlertDialog.Builder(ctx)
        .setTitle(R.string.details)
        .setMessage(lines.joinToString("\n"))
        .setPositiveButton(R.string.call) { _, _ -> SipManager.call(number) }
        .setNegativeButton(R.string.close, null)
        .show()
}
