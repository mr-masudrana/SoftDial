package com.example.softdial

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SoftFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Prefs.init(this)
        Prefs.fcmToken = token
        SipManager.init(this)
        if (Prefs.hasAccount()) SipManager.register()
    }

    /** A push from the SIP server wakes the app: start the service so SIP can register and ring. */
    override fun onMessageReceived(message: RemoteMessage) {
        SipManager.init(this)
        CallService.start(this)
    }
}
