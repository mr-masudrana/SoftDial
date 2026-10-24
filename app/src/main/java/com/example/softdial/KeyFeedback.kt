package com.example.softdial

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

/** Dial pad sound (DTMF tones) and a short vibration when a key is pressed. */
object KeyFeedback {
    private var tg: ToneGenerator? = null

    fun press(ctx: Context, key: String) {
        if (Prefs.keySound) tone(ctx, key)
        buzz(ctx)
    }

    fun buzz(ctx: Context) {
        if (!Prefs.keyVibrate) return
        try {
            ctx.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { /* no vibrator */ }
    }

    private fun tone(ctx: Context, key: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val t = when (key) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            if (tg == null) tg = ToneGenerator(AudioManager.STREAM_DTMF, 70)
            tg?.startTone(t, 120)
        } catch (e: Exception) {
            tg = null
        }
    }

    fun release() {
        tg?.release()
        tg = null
    }
}
