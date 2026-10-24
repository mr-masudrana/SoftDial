package com.example.softdial

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var list: ListView
    /** Parallel to the rows on screen: null for section headers / the empty message. */
    private var shown: List<Store.Entry?> = emptyList()
    private var hasItems = false
    private val fmtFull = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    private val fmtTime = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val fmtDay = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.nav_recents)
        list = ListView(this)
        styleCardList(list)
        setContentView(BottomNav.wrap(this, list, BottomNav.RECENTS))

        // tap = details, long press = menu
        list.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { showDetails(it) }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val e = shown.getOrNull(pos)
            if (e != null) showMenu(e)
            e != null
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        Prefs.historySeen = System.currentTimeMillis()
        BottomNav.refreshBadge(this)
    }

    private fun typeLabel(e: Store.Entry): String = when {
        e.note == "fwd" -> "↪ " + getString(R.string.hist_forwarded)
        e.note == "blocked" -> "⛔ " + getString(R.string.hist_blocked)
        e.note == "dnd" -> "🔕 " + getString(R.string.hist_blocked_dnd)
        e.missed -> "✗ " + getString(R.string.hist_missed)
        e.incoming -> "↙ " + getString(R.string.hist_incoming)
        else -> "↗ " + getString(R.string.hist_outgoing)
    }

    private fun dayLabel(t: Long): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startToday = cal.timeInMillis
        return when {
            t >= startToday -> getString(R.string.day_today)
            t >= startToday - 24L * 3600 * 1000 -> getString(R.string.day_yesterday)
            else -> fmtDay.format(Date(t))
        }
    }

    private fun reload() {
        val items = Store.history()
        hasItems = items.isNotEmpty()
        val rows = mutableListOf<EntryRow>()
        val map = mutableListOf<Store.Entry?>()

        if (items.isEmpty()) {
            rows.add(EntryRow(getString(R.string.calls_none), "", null))
            map.add(null)
        } else {
            var lastDay = ""
            for (e in items) {
                val day = dayLabel(e.time)
                if (day != lastDay) {
                    rows.add(EntryRow(day, "", null, header = true))
                    map.add(null)
                    lastDay = day
                }
                val tail = if (e.duration > 0) " · " + fmtDuration(e.duration) else ""
                rows.add(
                    EntryRow(
                        Store.nameFor(e.number) ?: e.number,
                        typeLabel(e) + " · " + fmtTime.format(Date(e.time)) + tail,
                        e.number,
                        e.missed
                    )
                )
                map.add(e)
            }
        }
        shown = map
        list.keepScroll {
            list.adapter = EntryAdapter(this, rows, R.layout.item_card) { r ->
                r.number?.let { SipManager.call(it); BottomNav.openDialer(this) }
            }
        }
        invalidateOptionsMenu()
    }

    private fun showMenu(e: Store.Entry) {
        val contact = Store.contactFor(e.number)
        val labels = arrayOf(
            getString(R.string.menu_copy_number),
            getString(if (contact != null) R.string.menu_edit_contact else R.string.menu_add_contact),
            getString(R.string.hist_block_number),
            getString(R.string.delete),
            getString(R.string.details)
        )
        AlertDialog.Builder(this)
            .setTitle(contact?.name ?: e.number)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> copyNumber(this, e.number)
                    1 -> if (contact != null) {
                        showContactDialog(this, contact.number, contact.name, contact.number) { reload() }
                    } else {
                        showContactDialog(this, null, "", e.number) { reload() }
                    }
                    2 -> {
                        Store.blockNumber(e.number)
                        Toast.makeText(this, getString(R.string.blocked_toast, e.number), Toast.LENGTH_SHORT).show()
                    }
                    3 -> { Store.deleteEntry(e.time); reload() }
                    4 -> showDetails(e)
                }
            }
            .show()
    }

    private fun showDetails(e: Store.Entry) {
        val contact = Store.contactFor(e.number)
        val total = Store.history().count { Store.same(it.number, e.number) }
        val lines = mutableListOf<String>()
        lines.add(getString(R.string.det_name) + ": " + (contact?.name ?: getString(R.string.det_unknown)))
        lines.add(getString(R.string.det_number) + ": " + e.number)
        lines.add(getString(R.string.det_type) + ": " + typeLabel(e))
        lines.add(getString(R.string.det_time) + ": " + fmtFull.format(Date(e.time)))
        lines.add(getString(R.string.det_duration) + ": " + fmtDuration(e.duration))
        lines.add(getString(R.string.det_total_calls) + ": " + total)
        AlertDialog.Builder(this)
            .setTitle(R.string.details)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(R.string.call) { _, _ -> SipManager.call(e.number); BottomNav.openDialer(this) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_clear)?.isVisible = hasItems
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_clear) {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_all_title)
                .setMessage(R.string.clear_all_msg)
                .setPositiveButton(R.string.ok) { _, _ ->
                    Store.clearHistory()
                    reload()
                    BottomNav.refreshBadge(this)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
