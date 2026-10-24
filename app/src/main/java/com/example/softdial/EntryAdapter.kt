package com.example.softdial

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat

/**
 * One row in a list.
 * - [number] == null: plain message row (no call button / avatar)
 * - [alert]: title in red (e.g. missed calls)
 * - [header]: section header (not clickable)
 * - [star]: null = no star button, otherwise filled / outline star
 */
class EntryRow(
    val title: String,
    val subtitle: String,
    val number: String?,
    val alert: Boolean = false,
    val header: Boolean = false,
    val star: Boolean? = null
)

/** List rows with optional section headers, avatar, star and call button. */
class EntryAdapter(
    ctx: Context,
    private val rows: List<EntryRow>,
    private val layoutRes: Int = R.layout.item_entry,
    private val onStar: ((EntryRow) -> Unit)? = null,
    private val onCall: (EntryRow) -> Unit
) : ArrayAdapter<EntryRow>(ctx, 0, rows) {

    private val normalColors by lazy {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        ContextCompat.getColorStateList(context, tv.resourceId)
    }

    private fun initial(title: String): String {
        val c = title.firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return if (c.isDigit()) "#" else c.uppercase()
    }

    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int = if (rows[position].header) 1 else 0
    override fun isEnabled(position: Int): Boolean = !rows[position].header
    override fun areAllItemsEnabled(): Boolean = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val r = rows[position]
        val inflater = LayoutInflater.from(context)

        if (r.header) {
            val h = convertView ?: inflater.inflate(R.layout.item_header, parent, false)
            (h as TextView).text = r.title
            return h
        }

        val v = convertView ?: inflater.inflate(layoutRes, parent, false)

        v.findViewById<TextView>(R.id.entryTitle).apply {
            text = r.title
            if (r.alert) setTextColor(ContextCompat.getColor(context, R.color.perm_bad))
            else normalColors?.let { setTextColor(it) }
        }
        v.findViewById<TextView>(R.id.entrySub).apply {
            text = r.subtitle
            visibility = if (r.subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.entryAvatar)?.apply {
            if (r.number == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = initial(r.title)
            }
        }
        v.findViewById<ImageButton>(R.id.entryStar)?.apply {
            val s = r.star
            if (s == null || r.number == null) {
                visibility = View.GONE
                setOnClickListener(null)
            } else {
                visibility = View.VISIBLE
                setImageResource(if (s) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(context, if (s) R.color.star_on else R.color.nav_inactive)
                    )
                )
                setOnClickListener { onStar?.invoke(r) }
            }
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
