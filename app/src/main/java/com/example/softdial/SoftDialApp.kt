package com.example.softdial

import android.app.Application

class SoftDialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppLog.init(this)
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        AppLog.d("APP", "Started, version $ver, Android ${android.os.Build.VERSION.SDK_INT}")
        Store.init(this)
        Store.migrateLegacy()
        Prefs.applyTheme()
        Prefs.applyLanguage()
    }
}
