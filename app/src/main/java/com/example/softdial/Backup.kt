package com.example.softdial

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Export / import of settings, SIP accounts and contacts as one JSON file (optionally encrypted). */
object Backup {

    fun isEncrypted(text: String): Boolean =
        try { JSONObject(text).has("enc") } catch (e: Exception) { false }

    fun build(passphrase: String): String {
        require(passphrase.length >= 10) { "Use a backup passphrase of at least 10 characters" }
        val root = JSONObject().put("version", 1).put("app", "softdial")

        root.put(
            "settings",
            JSONObject()
                .put("theme", Prefs.theme)
                .put("autoRecord", Prefs.autoRecord)
                .put("pushEnabled", Prefs.pushEnabled)
                .put("defaultAccountId", Prefs.defaultAccountId)
                .put("dnd", Prefs.dnd)
                .put("dndAllowContacts", Prefs.dndAllowContacts)
                .put("fwdMode", Prefs.fwdMode)
                .put("fwdNumber", Prefs.fwdNumber)
                .put("fwdDelay", Prefs.fwdDelay)
        )

        val accs = JSONArray()
        Store.accounts().forEach {
            accs.put(
                JSONObject().put("id", it.id).put("label", it.label).put("server", it.server)
                    .put("user", it.user).put("pass", it.pass).put("transport", it.transport)
            )
        }
        root.put("accounts", accs)

        val cons = JSONArray()
        Store.contacts().forEach { cons.put(JSONObject().put("name", it.name).put("number", it.number)) }
        root.put("contacts", cons)

        val plain = root.toString()
        return encrypt(plain, passphrase)
    }

    /** @return a short summary message. Throws IllegalArgumentException on bad file / passphrase. */
    fun restore(text: String, passphrase: String): String {
        if (text.length > 2 * 1024 * 1024) throw IllegalArgumentException("Backup file is too large (2 MB limit)")
        var root = try { JSONObject(text) } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid backup file")
        }
        if (!root.has("enc")) throw IllegalArgumentException("Unencrypted backups are not accepted. Export a new encrypted backup.")
        root = try { JSONObject(decrypt(root, passphrase)) } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException("Invalid backup data")
        }
        if (root.optInt("version", 0) != 1 || root.optString("app") != "softdial") {
            throw IllegalArgumentException("Not a Soft Dial backup")
        }

        val accountsArray = root.optJSONArray("accounts") ?: JSONArray()
        if (accountsArray.length() > 100) throw IllegalArgumentException("Too many SIP accounts")
        val validatedAccounts = mutableListOf<Store.SipAccount>()
        for (i in 0 until accountsArray.length()) {
            val o = accountsArray.optJSONObject(i) ?: throw IllegalArgumentException("Invalid account entry")
            val id = o.optString("id").take(128)
            val label = o.optString("label").take(120)
            val server = o.optString("server").trim().take(253)
            val user = o.optString("user").trim().take(256)
            val pass = o.optString("pass").take(1024)
            val transport = o.optString("transport", "udp").lowercase()
            if (id.isBlank() || label.isBlank() || server.isBlank() || user.isBlank() || pass.isEmpty())
                throw IllegalArgumentException("Account fields are missing")
            if (transport !in setOf("udp", "tcp", "tls")) throw IllegalArgumentException("Unsupported SIP transport")
            if (server.any { it.isWhitespace() || it == '/' || it == '@' }) throw IllegalArgumentException("Invalid SIP server")
            validatedAccounts.add(Store.SipAccount(id, label, server, user, pass, transport))
        }
        val contactsArray = root.optJSONArray("contacts") ?: JSONArray()
        if (contactsArray.length() > 10000) throw IllegalArgumentException("Too many contacts")
        val validatedContacts = mutableListOf<Store.Contact>()
        for (i in 0 until contactsArray.length()) {
            val o = contactsArray.optJSONObject(i) ?: throw IllegalArgumentException("Invalid contact entry")
            val name = o.optString("name").trim()
            val number = o.optString("number").trim()
            if (name.isBlank() || number.isBlank() || name.length > 200 || number.length > 100)
                throw IllegalArgumentException("Invalid contact fields")
            validatedContacts.add(Store.Contact(name, number))
        }

        root.optJSONObject("settings")?.let { s ->
            val theme = s.optString("theme", Prefs.theme)
            val forwarding = s.optString("fwdMode", Prefs.fwdMode)
            if (theme !in setOf("system", "light", "dark")) throw IllegalArgumentException("Invalid theme setting")
            if (forwarding !in setOf("off", "always", "busy", "noanswer")) throw IllegalArgumentException("Invalid forwarding setting")
            Prefs.theme = theme
            Prefs.autoRecord = s.optBoolean("autoRecord", Prefs.autoRecord)
            Prefs.pushEnabled = s.optBoolean("pushEnabled", Prefs.pushEnabled)
            Prefs.dnd = s.optBoolean("dnd", Prefs.dnd)
            Prefs.dndAllowContacts = s.optBoolean("dndAllowContacts", Prefs.dndAllowContacts)
            Prefs.fwdMode = s.optString("fwdMode", Prefs.fwdMode)
            Prefs.fwdNumber = s.optString("fwdNumber", Prefs.fwdNumber)
            Prefs.fwdDelay = s.optInt("fwdDelay", Prefs.fwdDelay).coerceIn(5, 120)
            val def = s.optString("defaultAccountId", "")
            if (def.isNotEmpty()) Prefs.defaultAccountId = def
        }

        validatedAccounts.forEach { Store.saveAccount(it) }
        val accCount = validatedAccounts.size
        val savedIds = Store.accounts().map { it.id }.toSet()
        if (Prefs.defaultAccountId !in savedIds) {
            Prefs.defaultAccountId = Store.accounts().firstOrNull()?.id ?: ""
        }
        val added = Store.addContacts(validatedContacts)

        return "Restored settings, $accCount account(s), $added new contact(s)"
    }

    // ---- encryption: AES-256-GCM, key from passphrase via PBKDF2 ----

    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120_000, 256))
        return SecretKeySpec(k.encoded, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().put("enc", 1).put("salt", b64(salt)).put("iv", b64(iv))
            .put("data", b64(data)).toString()
    }

    private fun decrypt(o: JSONObject, pass: String): String {
        if (pass.isEmpty()) throw IllegalArgumentException("Passphrase required")
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                key(pass, unb64(o.getString("salt"))),
                GCMParameterSpec(128, unb64(o.getString("iv")))
            )
            return String(c.doFinal(unb64(o.getString("data"))), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong passphrase or damaged file")
        }
    }
}
