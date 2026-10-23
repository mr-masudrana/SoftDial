package com.example.softdial

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Tiny local storage for contacts and call history (JSON in SharedPreferences). */
object Store {
    data class Contact(val name: String, val number: String)
    data class Entry(
        val number: String,
        val incoming: Boolean,
        val missed: Boolean,
        val duration: Int,
        val time: Long,
        val note: String = "",   // "", "dnd" or "fwd"
        val id: String = java.util.UUID.randomUUID().toString()
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
            // One-time migration from legacy plaintext account JSON to encrypted storage.
            if (sp.contains("accounts") && !sp.contains("accounts_secure")) {
                val legacy = sp.getString("accounts", "[]") ?: "[]"
                val encrypted = encryptLocal(legacy)
                sp.edit().putString("accounts_secure", encrypted).remove("accounts").apply()
            }
        }
    }

    private fun norm(s: String) = s.filter { it.isDigit() || it == '+' }

    private fun storageKey(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("softdial_accounts_aes", null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(
            "softdial_accounts_aes",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }

    private fun encryptLocal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, storageKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptLocal(encoded: String): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        require(all.size > 12) { "Invalid encrypted account storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, storageKey(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }

    // ---- SIP accounts ----

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun accounts(): List<SipAccount> {
        val encrypted = sp.getString("accounts_secure", null)
        val json = if (encrypted != null) decryptLocal(encrypted) else sp.getString("accounts", "[]") ?: "[]"
        val a = JSONArray(json)
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
        // Credentials are encrypted at rest with an Android Keystore AES-GCM key.
        val encrypted = encryptLocal(a.toString())
        sp.edit().putString("accounts_secure", encrypted).remove("accounts").apply()
    }

    fun saveAccount(acc: SipAccount) {
        val list = accounts()
        putAccounts(if (list.any { it.id == acc.id }) list.map { if (it.id == acc.id) acc else it } else list + acc)
    }

    fun deleteAccount(id: String) {
        val remaining = accounts().filter { it.id != id }
        putAccounts(remaining)
        if (Prefs.defaultAccountId == id) Prefs.defaultAccountId = remaining.firstOrNull()?.id ?: ""
    }

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
        }
        // Remove legacy plaintext credentials whether migrated or superseded by an account list.
        Prefs.server = ""
        Prefs.user = ""
        Prefs.pass = ""
    }

    // ---- contacts ----

    fun contacts(): List<Contact> {
        val a = JSONArray(sp.getString("contacts", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Contact(o.getString("name"), o.getString("number"))
        }.sortedBy { it.name.lowercase() }
    }

    private fun putContacts(list: List<Contact>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("number", it.number)) }
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

    fun deleteContact(number: String) {
        putContacts(contacts().filter { norm(it.number) != norm(number) })
    }

    fun nameFor(number: String): String? {
        val n = norm(number)
        if (n.isEmpty()) return null
        // Exact normalized match only: suffix matching can confuse unrelated numbers.
        return contacts().firstOrNull { norm(it.number) == n }?.name
    }

    // ---- history ----

    fun history(): List<Entry> {
        val a = JSONArray(sp.getString("history", "[]"))
        var migrated = false
        val entries = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val id = o.optString("id", "").ifBlank {
                migrated = true
                java.util.UUID.randomUUID().toString()
            }
            Entry(
                o.getString("number"), o.getBoolean("incoming"), o.getBoolean("missed"),
                o.getInt("duration"), o.getLong("time"), o.optString("note", ""), id
            )
        }
        if (migrated) putHistory(entries)
        return entries
    }

    private fun putHistory(list: List<Entry>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("number", it.number).put("incoming", it.incoming)
                    .put("missed", it.missed).put("duration", it.duration).put("time", it.time)
                    .put("note", it.note).put("id", it.id)
            )
        }
        sp.edit().putString("history", a.toString()).apply()
    }

    fun addEntry(e: Entry) { putHistory((listOf(e) + history()).take(200)) }
    fun deleteEntry(id: String) { putHistory(history().filter { it.id != id }) }
    fun clearHistory() { putHistory(emptyList()) }
}
