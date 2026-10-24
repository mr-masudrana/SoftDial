package com.example.softdial

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.linphone.core.Call

/**
 * Keeps the process alive so SIP stays registered and incoming calls can ring
 * while the app is in the background.
 */
class CallService : Service() {

    companion object {
        const val ACTION_DECLINE = "softdial.DECLINE"
        const val ACTION_HANGUP = "softdial.HANGUP"
        private const val CH_SERVICE = "service"
        private const val CH_INCOMING = "incoming"
        private const val CH_MISSED = "missed"
        private const val ID_ONGOING = 1
        private const val ID_INCOMING = 2
        private const val ID_MISSED = 3

        fun start(context: Context) {
            Prefs.init(context)
            val micOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (Prefs.hasAccount() && micOk) {
                ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            }
        }
    }

    private lateinit var nm: NotificationManager

    private val callCb: (Call.State, Call) -> Unit = { state, call ->
        when (state) {
            Call.State.IncomingReceived -> showIncoming(call)
            Call.State.Connected -> {
                cancelIncomingIfNone()
                // app is in foreground when a call is answered: safe moment to add the mic type
                startFg(ongoing(SipManager.lastRegistration), true)
            }
            Call.State.StreamsRunning, Call.State.End, Call.State.Error -> cancelIncomingIfNone()
            Call.State.Released -> {
                cancelIncomingIfNone()
                if (call.callLog?.status == Call.Status.Missed || SipManager.autoNote(call) == "dnd") showMissed(call)
            }
            else -> {}
        }
    }
    private val regCb: (String) -> Unit = { nm.notify(ID_ONGOING, ongoing(it)) }

    private fun cancelIncomingIfNone() {
        if (SipManager.incomingCall() == null) nm.cancel(ID_INCOMING)
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        createChannels()
        SipManager.init(this)
        startFg(ongoing(SipManager.lastRegistration), true)
        SipManager.addCallListener(callCb)
        SipManager.addRegListener(regCb)
    }

    private fun startFg(n: Notification, withMic: Boolean) {
        var base = 0
        if (Build.VERSION.SDK_INT >= 34) base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        var type = base
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (withMic && micOk && Build.VERSION.SDK_INT >= 30) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            ServiceCompat.startForeground(this, ID_ONGOING, n, type)
        } catch (e: Exception) {
            // mic type not allowed from background (e.g. started by a push): fall back
            ServiceCompat.startForeground(this, ID_ONGOING, n, base)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DECLINE -> {
                SipManager.declineIncoming()
                nm.cancel(ID_INCOMING)
            }
            ACTION_HANGUP -> SipManager.hangUp()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        SipManager.removeCallListener(callCb)
        SipManager.removeRegListener(regCb)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, getString(R.string.ch_status), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, getString(R.string.ch_incoming), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MISSED, getString(R.string.ch_missed), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(requestCode: Int, answer: Boolean): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ANSWER, answer)
        }
        return PendingIntent.getActivity(
            this, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ongoing(status: String): Notification =
        NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_sip, status))
            .setOngoing(true)
            .setContentIntent(openApp(10, false))
            .build()

    private fun who(call: Call): String {
        val num = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
        return Store.nameFor(num) ?: num
    }

    private fun showIncoming(call: Call) {
        val decline = PendingIntent.getService(
            this, 20,
            Intent(this, CallService::class.java).setAction(ACTION_DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CH_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(getString(R.string.state_incoming))
            .setContentText(who(call))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp(11, false))
            .setFullScreenIntent(openApp(12, false), true)
            .addAction(android.R.drawable.sym_action_call, getString(R.string.btn_answer), openApp(13, true))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_decline), decline)
            .build()
        nm.notify(ID_INCOMING, n)
    }

    private fun showMissed(call: Call) {
        val n = NotificationCompat.Builder(this, CH_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(getString(R.string.notif_missed))
            .setContentText(who(call))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(openApp(14, false))
            .build()
        nm.notify(ID_MISSED, n)
    }
}
