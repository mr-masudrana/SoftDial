package com.example.softdial

import android.app.Application

class SoftDialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Store.migrateLegacy()
        Prefs.applyTheme()
        Prefs.applyLanguage()
    }
}
