package com.example.softdial

import android.Manifest
import android.content.Context
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = ListView(this)
        setContentView(list)

        list.setOnItemClickListener { _, _, pos, _ ->
            val c = items[pos]
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

    private fun reload() {
        items = Store.contacts()
        val rows = if (items.isEmpty()) listOf("No contacts yet. Tap Add.")
        else items.map { "${it.name}\n${it.number}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) importFromPhone()
            else Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show()
        }

    private fun importFromPhone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        val found = mutableListOf<Store.Contact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num = (c.getString(1) ?: continue).filter { it.isDigit() || it == '+' }
                if (num.isNotEmpty()) found.add(Store.Contact(name, num))
            }
        }
        val added = Store.addContacts(found)
        reload()
        Toast.makeText(this, "Imported $added new contacts", Toast.LENGTH_LONG).show()
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
            R.id.menu_import -> { importFromPhone(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
