package com.example.softdial

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private var items: List<Store.Entry> = emptyList()
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Recents"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = ListView(this)
        setContentView(list)

        list.setOnItemClickListener { _, _, pos, _ ->
            val e = items.getOrNull(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setTitle(Store.nameFor(e.number) ?: e.number)
                .setItems(arrayOf("Call", "Save to contacts", "Delete")) { _, which ->
                    when (which) {
                        0 -> { SipManager.call(e.number); finish() }
                        1 -> showContactDialog(this, null, Store.nameFor(e.number) ?: "", e.number) { reload() }
                        2 -> { Store.deleteEntry(e.time); reload() }
                    }
                }
                .show()
        }
        reload()
    }

    private fun reload() {
        items = Store.history()
        val rows = if (items.isEmpty()) listOf("No calls yet.") else items.map { e ->
            val icon = when {
                e.note == "fwd" -> "↪ Forwarded"
                e.note == "dnd" -> "🔕 Blocked (DND)"
                e.missed -> "✗ Missed"
                e.incoming -> "↙ Incoming"
                else -> "↗ Outgoing"
            }
            val name = Store.nameFor(e.number) ?: e.number
            val dur = "%d:%02d".format(e.duration / 60, e.duration % 60)
            "$name\n$icon · ${fmt.format(Date(e.time))} · $dur"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_clear) {
            Store.clearHistory(); reload(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
