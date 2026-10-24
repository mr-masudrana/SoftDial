package com.example.softdial

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object Push {
    /** True only when the APK was built with google-services.json. */
    fun available(ctx: Context): Boolean = FirebaseApp.getApps(ctx).isNotEmpty()

    fun projectId(ctx: Context): String =
        if (available(ctx)) FirebaseApp.getInstance().options.projectId ?: "" else ""

    fun refreshToken(ctx: Context) {
        if (!available(ctx)) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
            if (t != Prefs.fcmToken) {
                Prefs.fcmToken = t
                AppLog.d("PUSH", "FCM token updated")
                if (Prefs.hasAccount()) SipManager.register()
            }
        }
    }
}
