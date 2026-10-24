package com.example.softdial

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

fun showAccountDialog(ctx: Context, existing: Store.SipAccount?, onDone: () -> Unit) {
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }
    val etLabel = EditText(ctx).apply { hint = ctx.getString(R.string.acc_label_hint); setText(existing?.label ?: "") }
    val etServer = EditText(ctx).apply {
        hint = ctx.getString(R.string.acc_server_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setText(existing?.server ?: "")
    }
    val etUser = EditText(ctx).apply { hint = ctx.getString(R.string.acc_user_hint); setText(existing?.user ?: "") }
    val etPass = EditText(ctx).apply {
        hint = ctx.getString(R.string.acc_pass_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setText(existing?.pass ?: "")
    }
    val spinner = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, listOf("UDP", "TCP", "TLS"))
        setSelection(
            when (existing?.transport) { "tcp" -> 1; "tls" -> 2; else -> 0 }
        )
    }
    listOf(etLabel, etServer, etUser, etPass, spinner).forEach { box.addView(it) }

    AlertDialog.Builder(ctx)
        .setTitle(ctx.getString(if (existing == null) R.string.acc_add_title else R.string.acc_edit_title))
        .setView(ScrollView(ctx).apply { addView(box) })
        .setPositiveButton(ctx.getString(R.string.save)) { _, _ ->
            val server = etServer.text.toString().trim()
            val user = etUser.text.toString().trim()
            if (server.isNotEmpty() && user.isNotEmpty()) {
                val transport = when (spinner.selectedItemPosition) { 1 -> "tcp"; 2 -> "tls"; else -> "udp" }
                val label = etLabel.text.toString().trim().ifEmpty { "$user@$server" }
                val acc = Store.SipAccount(
                    existing?.id ?: Store.newId(), label, server, user,
                    etPass.text.toString(), transport
                )
                Store.saveAccount(acc)
                if (Prefs.defaultAccountId.isEmpty()) Prefs.defaultAccountId = acc.id
                onDone()
            }
        }
        .setNegativeButton(ctx.getString(R.string.cancel), null)
        .show()
}

class AccountsActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private var items: List<Store.SipAccount> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.acc_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = ListView(this)
        setContentView(list)

        list.setOnItemClickListener { _, _, pos, _ ->
            val a = items.getOrNull(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(arrayOf(getString(R.string.acc_use_outgoing), getString(R.string.edit), getString(R.string.delete))) { _, which ->
                    when (which) {
                        0 -> { Prefs.defaultAccountId = a.id; SipManager.applySelected(); reload() }
                        1 -> showAccountDialog(this, a) { changed() }
                        2 -> { Store.deleteAccount(a.id); changed() }
                    }
                }
                .show()
        }
        reload()
    }

    private val regCb: (String) -> Unit = { _ -> runOnUiThread { reload() } }

    override fun onStart() {
        super.onStart()
        SipManager.addRegListener(regCb)
        reload()
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        super.onStop()
    }

    private fun changed() {
        SipManager.register()
        CallService.start(this)
        reload()
    }

    private fun reload() {
        items = Store.accounts()
        val sel = Store.selectedAccount()?.id
        val rows = if (items.isEmpty()) listOf(getString(R.string.acc_none))
        else items.map {
            "${it.label}${if (it.id == sel) ("  " + getString(R.string.acc_default_mark)) else ""}\n${it.user}@${it.server} · ${it.transport.uppercase()}\n● ${regText(this, it)}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.accounts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_add_account) {
            showAccountDialog(this, null) { changed() }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
