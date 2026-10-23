package com.example.softdial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Copies contacts from the phone's address book into the app's own contact list. */
object PhoneBook {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** @return number of NEW contacts added (existing numbers are skipped). */
    fun importAll(ctx: Context): Int {
        if (!hasPermission(ctx)) return 0
        val found = mutableListOf<Store.Contact>()
        ctx.contentResolver.query(
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
        return Store.addContacts(found)
    }
}
