package com.example.softdial

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Plays the chosen ringtone for incoming calls. Used only when a custom ringtone (or silent)
 * is selected; with the default ringtone Linphone rings by itself.
 */
object Ringer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }

    private fun toneUri(): Uri? = when (val s = Prefs.ringtoneUri) {
        "silent" -> null
        "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        else -> Uri.parse(s)
    }

    /** Name of the selected ringtone for display. */
    fun currentTitle(ctx: Context): String {
        val s = Prefs.ringtoneUri
        return when (s) {
            "" -> ctx.getString(R.string.ring_default)
            "silent" -> ctx.getString(R.string.ring_silent)
            else -> try {
                RingtoneManager.getRingtone(ctx, Uri.parse(s))?.getTitle(ctx) ?: s
            } catch (e: Exception) { s }
        }
    }

    fun start(ctx: Context) {
        stop()
        val am = ctx.getSystemService(AudioManager::class.java)
        val mode = am.ringerMode
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        if (mode == AudioManager.RINGER_MODE_NORMAL) playTone(ctx)
        if (Prefs.ringVibrate) vibrate(ctx)
    }

    /** Plays the current setting for a few seconds (the Test button). */
    fun preview(ctx: Context) {
        start(ctx)
        handler.postDelayed(stopRunnable, 4000)
    }

    private fun playTone(ctx: Context) {
        val uri = toneUri() ?: return
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            AppLog.d("RING", "Cannot play ringtone: ${e.message}")
            stop()
        }
    }

    private fun vibrate(ctx: Context) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }
}
