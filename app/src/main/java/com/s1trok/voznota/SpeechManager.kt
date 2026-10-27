package com.s1trok.voznota

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Escucha continua sobre SpeechRecognizer nativo con supresión de pitidos del sistema.
 * - Silencia temporalmente los tonos del sistema durante la escucha continua.
 * - Activa el modo dictado para eliminar el sonido al re-escuchar.
 * - Una sola sesión a la vez (evita ERROR_RECOGNIZER_BUSY).
 * Debe usarse siempre desde el hilo principal.
 */
class SpeechManager(
    context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onListeningChanged(listening: Boolean)
        fun onFatalError(message: String)
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var failures = 0

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra("android.speech.extra.DICTATION_MODE", true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(callbacks::onPartial)
        }

        override fun onResults(results: Bundle?) {
            failures = 0
            firstResult(results)?.let(callbacks::onFinal)
            scheduleListen(RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleListen(RESTART_DELAY_MS) // silencio: normal

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    recreateRecognizer()
                    scheduleListen(1000)
                }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fatal("Falta el permiso de micrófono.")

                12, 13 -> // LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE
                    fatal("Idioma español no disponible en el motor de voz. Descárgalo en los ajustes de Google.")

                else -> { // red, audio, servidor...
                    failures++
                    if (failures >= MAX_FAILURES) {
                        fatal("El reconocimiento falla repetidamente (código $error). Revisa tu conexión o el motor de voz.")
                    } else {
                        recreateRecognizer()
                        scheduleListen((RESTART_DELAY_MS shl failures).coerceAtMost(4000))
                    }
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {} // NO reiniciar aquí: onResults/onError lo harán
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (active) return
        if (!isAvailable()) {
            callbacks.onFatalError("Este dispositivo no tiene un motor de reconocimiento de voz habilitado.")
            return
        }
        active = true
        failures = 0
        muteSystemBeepSounds()
        callbacks.onListeningChanged(true)
        recreateRecognizer()
        scheduleListen(0)
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        restoreSystemBeepSounds()
        callbacks.onListeningChanged(false)
    }

    fun release() = stop()

    private fun scheduleListen(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!active) return@postDelayed
            try {
                muteSystemBeepSounds()
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                recreateRecognizer()
                scheduleListen(1000)
            }
        }, delayMs)
    }

    private fun recreateRecognizer() {
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
        }
    }

    private fun destroyRecognizer() {
        recognizer?.let {
            runCatching { it.setRecognitionListener(null) }
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun muteSystemBeepSounds() {
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreSystemBeepSounds() {
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fatal(message: String) {
        stop()
        callbacks.onFatalError(message)
    }

    private fun firstResult(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val RESTART_DELAY_MS = 300L
        const val MAX_FAILURES = 5
    }
}
