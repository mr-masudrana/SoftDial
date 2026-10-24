package com.example.softdial

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** List of blocked numbers. Calls from these numbers are rejected automatically. */
class BlockedActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private var items: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.blk_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = ListView(this)
        setContentView(list)

        list.setOnItemClickListener { _, _, pos, _ ->
            val n = items.getOrNull(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setTitle(Store.nameFor(n) ?: n)
                .setMessage(getString(R.string.blk_unblock_msg))
                .setPositiveButton(getString(R.string.blk_unblock)) { _, _ -> Store.unblock(n); reload() }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
        reload()
    }

    private fun reload() {
        items = Store.blocked()
        val rows = if (items.isEmpty()) {
            listOf(EntryRow(getString(R.string.blk_none), "", null))
        } else items.map { n -> EntryRow(Store.nameFor(n) ?: n, if (Store.nameFor(n) != null) n else "", null) }
        list.adapter = EntryAdapter(this, rows) { }
    }

    private fun addDialog() {
        val et = EditText(this).apply { hint = getString(R.string.number_hint); inputType = InputType.TYPE_CLASS_PHONE }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.blk_add_title))
            .setView(box)
            .setPositiveButton(getString(R.string.blk_block)) { _, _ ->
                Store.blockNumber(et.text.toString().trim())
                reload()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.blocked_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_add_block) { addDialog(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
