package com.example.softdial

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared add/edit dialog (also used from Recents). */
fun showContactDialog(ctx: Context, oldNumber: String?, name: String, number: String, onDone: () -> Unit) {
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }
    val etName = EditText(ctx).apply { hint = ctx.getString(R.string.name_hint); setText(name) }
    val etNum = EditText(ctx).apply {
        hint = ctx.getString(R.string.number_hint)
        inputType = InputType.TYPE_CLASS_PHONE
        setText(number)
    }
    box.addView(etName)
    box.addView(etNum)
    AlertDialog.Builder(ctx)
        .setTitle(ctx.getString(R.string.contact_dialog_title))
        .setView(box)
        .setPositiveButton(ctx.getString(R.string.save)) { _, _ ->
            val n = etNum.text.toString().trim()
            val nm = etName.text.toString().trim()
            if (n.isNotEmpty() && nm.isNotEmpty()) {
                val fav = oldNumber?.let { Store.contactFor(it)?.fav } ?: false
                if (oldNumber != null && oldNumber != n) Store.deleteContact(oldNumber)
                Store.saveContact(Store.Contact(nm, n, fav))
                onDone()
            }
        }
        .setNegativeButton(ctx.getString(R.string.cancel), null)
        .show()
}

class ContactsActivity : AppCompatActivity() {

    private lateinit var list: ListView
    /** Parallel to the rows on screen: null for section headers / the empty message. */
    private var shown: List<Store.Contact?> = emptyList()
    private var query = ""
    private val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.nav_contacts)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        list = ListView(this)
        styleCardList(list)
        val d = resources.displayMetrics.density
        val search = EditText(this).apply {
            hint = getString(R.string.search_contacts)
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
            setPadding(0, (8 * d).toInt(), 0, 0)
            addView(
                search,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins((16 * d).toInt(), 0, (16 * d).toInt(), (4 * d).toInt()) }
            )
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(BottomNav.wrap(this, box, BottomNav.CONTACTS))

        // tap = details, long press = menu
        list.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { showDetails(it) }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val c = shown.getOrNull(pos)
            if (c != null) showMenu(c)
            c != null
        }
        reload()
    }

    override fun onStart() {
        super.onStart()
        BottomNav.refreshBadge(this)
        reload()
    }

    private fun matches(c: Store.Contact): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val qd = q.filter { it.isDigit() || it == '+' }
        return c.name.lowercase().contains(q) ||
            (qd.isNotEmpty() && c.number.filter { it.isDigit() || it == '+' }.contains(qd))
    }

    private fun reload() {
        val filtered = Store.contacts().filter { matches(it) }
        val rows = mutableListOf<EntryRow>()
        val map = mutableListOf<Store.Contact?>()

        fun add(c: Store.Contact) {
            rows.add(EntryRow(c.name, c.number, c.number, star = c.fav))
            map.add(c)
        }
        fun header(text: String) {
            rows.add(EntryRow(text, "", null, header = true))
            map.add(null)
        }

        if (filtered.isEmpty()) {
            rows.add(
                EntryRow(
                    if (query.isBlank()) getString(R.string.contacts_none)
                    else getString(R.string.contacts_no_match, query.trim()),
                    "", null
                )
            )
            map.add(null)
        } else {
            val favs = filtered.filter { it.fav }
            val others = filtered.filter { !it.fav }
            if (favs.isNotEmpty() && query.isBlank()) {
                header(getString(R.string.fav_header))
                favs.forEach { add(it) }
                if (others.isNotEmpty()) {
                    header(getString(R.string.all_contacts))
                    others.forEach { add(it) }
                }
            } else {
                favs.forEach { add(it) }      // while searching: favorites first, no headers
                others.forEach { add(it) }
            }
        }
        shown = map
        list.keepScroll {
            list.adapter = EntryAdapter(
                this, rows, R.layout.item_card,
                onStar = { r ->
                    r.number?.let { n ->
                        Store.contactFor(n)?.let { c -> Store.setFavorite(c.number, !c.fav); reload() }
                    }
                }
            ) { r ->
                r.number?.let { SipManager.call(it); BottomNav.openDialer(this) }
            }
        }
    }

    private fun showMenu(c: Store.Contact) {
        val labels = arrayOf(
            getString(R.string.menu_copy_number),
            getString(if (c.fav) R.string.menu_remove_fav else R.string.menu_add_fav),
            getString(R.string.menu_edit_contact),
            getString(R.string.delete),
            getString(R.string.details)
        )
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> copyNumber(this, c.number)
                    1 -> { Store.setFavorite(c.number, !c.fav); reload() }
                    2 -> showContactDialog(this, c.number, c.name, c.number) { reload() }
                    3 -> confirmDelete(c)
                    4 -> showDetails(c)
                }
            }
            .show()
    }

    private fun confirmDelete(c: Store.Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.del_contact_title)
            .setMessage(c.name + "\n" + c.number)
            .setPositiveButton(R.string.delete) { _, _ -> Store.deleteContact(c.number); reload() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDetails(c: Store.Contact) {
        val calls = Store.history().filter { Store.same(it.number, c.number) }
        val lines = mutableListOf<String>()
        lines.add(getString(R.string.det_name) + ": " + c.name)
        lines.add(getString(R.string.det_number) + ": " + c.number)
        lines.add(getString(R.string.det_total_calls) + ": " + calls.size)
        calls.firstOrNull()?.let {
            lines.add(getString(R.string.det_last_call) + ": " + fmt.format(Date(it.time)))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.details)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(R.string.call) { _, _ -> SipManager.call(c.number); BottomNav.openDialer(this) }
            .setNeutralButton(R.string.menu_edit_contact) { _, _ ->
                showContactDialog(this, c.number, c.name, c.number) { reload() }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.contacts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_add) {
            showContactDialog(this, null, "", "") { reload() }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
