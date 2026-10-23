package com.example.softdial

import android.Manifest
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Shared add/edit dialog (also used from call history). */
fun showContactDialog(ctx: Context, oldNumber: String?, name: String, number: String, onDone: () -> Unit) {
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }
    val etName = EditText(ctx).apply { hint = "Name"; setText(name) }
    val etNum = EditText(ctx).apply {
        hint = "Number"
        inputType = InputType.TYPE_CLASS_PHONE
        setText(number)
    }
    box.addView(etName)
    box.addView(etNum)
    AlertDialog.Builder(ctx)
        .setTitle("Contact")
        .setView(box)
        .setPositiveButton("Save") { _, _ ->
            val n = etNum.text.toString().trim()
            val nm = etName.text.toString().trim()
            if (n.isNotEmpty() && nm.isNotEmpty()) {
                if (oldNumber != null && oldNumber != n) Store.deleteContact(oldNumber)
                Store.saveContact(Store.Contact(nm, n))
                onDone()
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

class ContactsActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private var items: List<Store.Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Contacts"
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        list = ListView(this)
        val d = resources.displayMetrics.density
        val search = EditText(this).apply {
            hint = "Search contacts"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = (8 * d).toInt()
            compoundDrawableTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this@ContactsActivity, R.color.nav_inactive))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    reload()
                }
            })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(BottomNav.wrap(this, box, BottomNav.CONTACTS))

        list.setOnItemClickListener { _, _, pos, _ ->
            val c = items.getOrNull(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setTitle("${c.name}\n${c.number}")
                .setItems(arrayOf("Call", "Edit", "Delete")) { _, which ->
                    when (which) {
                        0 -> { SipManager.call(c.number); finish() }
                        1 -> showContactDialog(this, c.number, c.name, c.number) { reload() }
                        2 -> { Store.deleteContact(c.number); reload() }
                    }
                }
                .show()
        }
        reload()
    }

    private var query = ""

    private fun matches(c: Store.Contact): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val qd = q.filter { it.isDigit() || it == '+' }
        return c.name.lowercase().contains(q) ||
            (qd.isNotEmpty() && c.number.filter { it.isDigit() || it == '+' }.contains(qd))
    }

    override fun onStart() {
        super.onStart()
        BottomNav.refreshBadge(this)
    }

    private fun reload() {
        items = Store.contacts().filter { matches(it) }
        val rows = if (items.isEmpty()) {
            listOf(
                EntryRow(
                    if (query.isBlank()) "No contacts yet. Tap Add." else "No matches for \"${query.trim()}\"",
                    "", null
                )
            )
        } else items.map { EntryRow(it.name, it.number, it.number) }
        list.adapter = EntryAdapter(this, rows) { r ->
            r.number?.let { SipManager.call(it); BottomNav.openDialer(this) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.contacts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                showContactDialog(this, null, "", "") { reload() }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
