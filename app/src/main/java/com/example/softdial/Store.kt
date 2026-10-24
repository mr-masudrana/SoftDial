package com.example.softdial

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Tiny local storage for contacts and call history (JSON in SharedPreferences). */
object Store {
    data class Contact(val name: String, val number: String, val fav: Boolean = false)
    data class Entry(
        val number: String,
        val incoming: Boolean,
        val missed: Boolean,
        val duration: Int,
        val time: Long,
        val note: String = ""   // "", "dnd" or "fwd"
    )

    data class SipAccount(
        val id: String,
        val label: String,
        val server: String,
        val user: String,
        val pass: String,
        val transport: String   // "udp" | "tcp" | "tls"
    )

    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("softdial_store", Context.MODE_PRIVATE)
        }
    }

    private fun norm(s: String) = s.filter { it.isDigit() || it == '+' }

    // ---- SIP accounts ----

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun accounts(): List<SipAccount> {
        val a = JSONArray(sp.getString("accounts", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SipAccount(
                o.getString("id"), o.getString("label"), o.getString("server"),
                o.getString("user"), o.getString("pass"), o.getString("transport")
            )
        }
    }

    private fun putAccounts(list: List<SipAccount>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("label", it.label).put("server", it.server)
                    .put("user", it.user).put("pass", it.pass).put("transport", it.transport)
            )
        }
        sp.edit().putString("accounts", a.toString()).apply()
    }

    fun saveAccount(acc: SipAccount) {
        val list = accounts()
        putAccounts(if (list.any { it.id == acc.id }) list.map { if (it.id == acc.id) acc else it } else list + acc)
    }

    fun deleteAccount(id: String) { putAccounts(accounts().filter { it.id != id }) }

    /** The account chosen for outgoing calls (falls back to the first one). */
    fun selectedAccount(): SipAccount? {
        val list = accounts()
        return list.firstOrNull { it.id == Prefs.defaultAccountId } ?: list.firstOrNull()
    }

    /** Moves the single account saved by older versions into the new list. */
    fun migrateLegacy() {
        if (accounts().isEmpty() && Prefs.server.isNotBlank() && Prefs.user.isNotBlank()) {
            val acc = SipAccount(
                newId(), "${Prefs.user}@${Prefs.server}", Prefs.server, Prefs.user,
                Prefs.pass, Prefs.transport
            )
            saveAccount(acc)
            Prefs.defaultAccountId = acc.id
            Prefs.server = ""
            Prefs.user = ""
            Prefs.pass = ""
        }
    }

    // ---- contacts ----

    fun contacts(): List<Contact> {
        val a = JSONArray(sp.getString("contacts", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Contact(o.getString("name"), o.getString("number"), o.optBoolean("fav", false))
        }.sortedBy { it.name.lowercase() }
    }

    private fun putContacts(list: List<Contact>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("number", it.number).put("fav", it.fav)) }
        sp.edit().putString("contacts", a.toString()).apply()
    }

    fun saveContact(c: Contact) {
        putContacts(contacts().filter { norm(it.number) != norm(c.number) } + c)
    }

    /** Adds contacts whose number is not already saved. Returns how many were added. */
    fun addContacts(found: List<Contact>): Int {
        val existing = contacts()
        val seen = existing.map { norm(it.number) }.toMutableSet()
        val fresh = found.filter { seen.add(norm(it.number)) }
        if (fresh.isNotEmpty()) putContacts(existing + fresh)
        return fresh.size
    }

    fun setFavorite(number: String, fav: Boolean) {
        putContacts(contacts().map { if (same(it.number, number)) it.copy(fav = fav) else it })
    }

    fun deleteContact(number: String) {
        putContacts(contacts().filter { norm(it.number) != norm(number) })
    }

    fun same(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (x.length >= 7 && y.length >= 7 && (x.endsWith(y) || y.endsWith(x)))
    }

    fun contactFor(number: String): Contact? = contacts().firstOrNull { same(it.number, number) }

    fun nameFor(number: String): String? = contactFor(number)?.name

    // ---- blocked numbers ----

    fun blocked(): List<String> {
        val a = JSONArray(sp.getString("blocked", "[]"))
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun putBlocked(list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        sp.edit().putString("blocked", a.toString()).apply()
    }

    fun isBlocked(number: String): Boolean {
        val n = norm(number)
        if (n.isEmpty()) return false
        return blocked().any {
            val m = norm(it)
            m == n || (m.length >= 7 && n.length >= 7 && (m.endsWith(n) || n.endsWith(m)))
        }
    }

    fun blockNumber(number: String) {
        val n = norm(number)
        if (n.isNotEmpty() && !isBlocked(n)) putBlocked(blocked() + n)
    }

    fun unblock(number: String) { putBlocked(blocked().filter { it != number }) }

    // ---- history ----

    fun history(): List<Entry> {
        val a = JSONArray(sp.getString("history", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Entry(
                o.getString("number"), o.getBoolean("incoming"), o.getBoolean("missed"),
                o.getInt("duration"), o.getLong("time"), o.optString("note", "")
            )
        }
    }

    private fun putHistory(list: List<Entry>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("number", it.number).put("incoming", it.incoming)
                    .put("missed", it.missed).put("duration", it.duration).put("time", it.time).put("note", it.note)
            )
        }
        sp.edit().putString("history", a.toString()).apply()
    }

    fun unseenMissed(since: Long): Int = history().count { it.missed && it.time > since }

    fun addEntry(e: Entry) { putHistory((listOf(e) + history()).take(200)) }
    fun deleteEntry(time: Long) { putHistory(history().filter { it.time != time }) }
    fun deleteEntriesFor(number: String) { putHistory(history().filter { !same(it.number, number) }) }
    fun clearHistory() { putHistory(emptyList()) }
}
