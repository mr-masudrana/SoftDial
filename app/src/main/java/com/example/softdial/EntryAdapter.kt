package com.example.softdial

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/** One row in a list. [number] == null means a plain message row (no call button). */
class EntryRow(val title: String, val subtitle: String, val number: String?)

/** List rows with a call button on the right (used by Recents, Contacts and dialer suggestions). */
class EntryAdapter(
    ctx: Context,
    private val rows: List<EntryRow>,
    private val onCall: (EntryRow) -> Unit
) : ArrayAdapter<EntryRow>(ctx, 0, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_entry, parent, false)
        val r = rows[position]
        v.findViewById<TextView>(R.id.entryTitle).text = r.title
        v.findViewById<TextView>(R.id.entrySub).apply {
            text = r.subtitle
            visibility = if (r.subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<View>(R.id.entryCall).apply {
            if (r.number == null) {
                visibility = View.GONE
                setOnClickListener(null)
            } else {
                visibility = View.VISIBLE
                setOnClickListener { onCall(r) }
            }
        }
        return v
    }
}
