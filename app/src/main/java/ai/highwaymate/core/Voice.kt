package ai.highwaymate.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Short spoken guidance. Uses the system TextToSpeech engine when one is available; otherwise a short tone.
 * Pleos TextToSpeech SDK is "in preparation", so the product plans pre-recorded clips until it opens.
 * Audio focus uses navigation-guidance usage so media ducks instead of stopping.
 */
class Voice(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).build()
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ttsReady = status == TextToSpeech.SUCCESS && runCatching {
            val r = ttsRef?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
            r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }.getOrDefault(false)
        Store.log("음성 엔진 ${if (ttsReady) "준비됨" else "없음 → 효과음으로 안내"}")
    }
    private val ttsRef: TextToSpeech? get() = runCatching { tts }.getOrNull()

    init {
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
        })
    }

    fun say(text: String, level: Level) {
        val mode = Settings.now.mode
        if (mode == AlertMode.SCREEN) return
        am.requestAudioFocus(focus)
        val chimeMs = chime(level)
        if (mode == AlertMode.VOICE && ttsReady) {
            main.postDelayed({ tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "hm-${System.nanoTime()}") }, chimeMs + 150)
        } else {
            main.postDelayed({ am.abandonAudioFocusRequest(focus) }, chimeMs + 400)
        }
    }

    /** Caution (yellow): one chime. Urgent (red): three quick chimes. Info: one soft chime. Returns its length. */
    private fun chime(level: Level): Long {
        val count = if (level == Level.URGENT) 3 else 1
        val tone = when (level) {
            Level.URGENT -> ToneGenerator.TONE_PROP_BEEP2
            Level.CAUTION -> ToneGenerator.TONE_PROP_BEEP
            Level.INFO -> ToneGenerator.TONE_PROP_ACK
        }
        runCatching {
            val t = ToneGenerator(AudioManager.STREAM_NOTIFICATION, if (level == Level.INFO) 60 else 85)
            repeat(count) { i -> main.postDelayed({ runCatching { t.startTone(tone, 180) } }, i * 260L) }
            main.postDelayed({ t.release() }, count * 260L + 400)
        }
        return count * 260L
    }

    fun release() = runCatching { tts.shutdown() }
}
