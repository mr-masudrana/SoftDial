package com.example.softdial

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("softdial", Context.MODE_PRIVATE)
    }

    var server: String
        get() = sp.getString("server", "") ?: ""
        set(v) = sp.edit().putString("server", v).apply()

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) = sp.edit().putString("user", v).apply()

    var pass: String
        get() = sp.getString("pass", "") ?: ""
        set(v) = sp.edit().putString("pass", v).apply()

    /** "udp", "tcp" or "tls" */
    var transport: String
        get() = sp.getString("transport", "udp") ?: "udp"
        set(v) = sp.edit().putString("transport", v).apply()

    var autoRecord: Boolean
        get() = sp.getBoolean("autoRecord", false)
        set(v) = sp.edit().putBoolean("autoRecord", v).apply()

    /** "system", "light" or "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    var pushEnabled: Boolean
        get() = sp.getBoolean("pushEnabled", true)
        set(v) = sp.edit().putBoolean("pushEnabled", v).apply()

    var fcmToken: String
        get() = sp.getString("fcmToken", "") ?: ""
        set(v) = sp.edit().putString("fcmToken", v).apply()

    /** "system", "en" or "bn" */
    var language: String
        get() = sp.getString("language", "system") ?: "system"
        set(v) = sp.edit().putString("language", v).apply()

    fun applyLanguage() {
        AppCompatDelegate.setApplicationLocales(
            when (language) {
                "en" -> LocaleListCompat.forLanguageTags("en")
                "bn" -> LocaleListCompat.forLanguageTags("bn")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        )
    }

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    var dnd: Boolean
        get() = sp.getBoolean("dnd", false)
        set(v) = sp.edit().putBoolean("dnd", v).apply()

    var dndAllowContacts: Boolean
        get() = sp.getBoolean("dndAllowContacts", true)
        set(v) = sp.edit().putBoolean("dndAllowContacts", v).apply()

    /** "off", "always", "busy" or "noanswer" */
    var fwdMode: String
        get() = sp.getString("fwdMode", "off") ?: "off"
        set(v) = sp.edit().putString("fwdMode", v).apply()

    var fwdNumber: String
        get() = sp.getString("fwdNumber", "") ?: ""
        set(v) = sp.edit().putString("fwdNumber", v).apply()

    var fwdDelay: Int
        get() = sp.getInt("fwdDelay", 20)
        set(v) = sp.edit().putInt("fwdDelay", v).apply()

    /** Time when the Recents screen was last opened (for the missed-call badge). */
    var historySeen: Long
        get() = sp.getLong("historySeen", 0L)
        set(v) = sp.edit().putLong("historySeen", v).apply()

    /** True once contacts were copied from the phone book after the permission was granted. */
    var contactsImported: Boolean
        get() = sp.getBoolean("contactsImported", false)
        set(v) = sp.edit().putBoolean("contactsImported", v).apply()

    var defaultAccountId: String
        get() = sp.getString("defaultAccountId", "") ?: ""
        set(v) = sp.edit().putString("defaultAccountId", v).apply()

    fun hasAccount() = Store.accounts().isNotEmpty()
}
