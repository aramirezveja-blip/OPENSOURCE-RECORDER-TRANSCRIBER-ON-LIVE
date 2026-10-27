package com.s1trok.voznota

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceState(
    val listening: Boolean = false,
    val draft: String = "",
    val partial: String = "",
    val message: String? = null,
)

class VoiceService : Service(), SpeechManager.Callbacks {

    private var speechManager: SpeechManager? = null

    override fun onCreate() {
        super.onCreate()
        speechManager = SpeechManager(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopServiceInternal()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification("Escuchando...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _serviceState.update { it.copy(message = null) }
        speechManager?.start()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcripción de VozNota",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para la transcripción continua en segundo plano"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingMainIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VozNota - Transcribiendo")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingMainIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun stopServiceInternal() {
        speechManager?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Callbacks de SpeechManager ---
    override fun onPartial(text: String) {
        _serviceState.update { it.copy(partial = text) }
    }

    override fun onFinal(text: String) {
        val updatedDraft = (_serviceState.value.draft + " " + text).trim()
        _serviceState.update {
            it.copy(draft = updatedDraft, partial = "")
        }
        updateNotification(text.take(40))
    }

    override fun onListeningChanged(listening: Boolean) {
        _serviceState.update { current ->
            if (!listening && current.partial.isNotBlank()) {
                current.copy(
                    listening = false,
                    draft = (current.draft + " " + current.partial).trim(),
                    partial = ""
                )
            } else {
                current.copy(listening = listening)
            }
        }
        if (!listening) {
            stopServiceInternal()
        }
    }

    override fun onFatalError(message: String) {
        _serviceState.update { it.copy(message = message, listening = false) }
        stopServiceInternal()
    }

    override fun onDestroy() {
        speechManager?.release()
        speechManager = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "voznota_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.s1trok.voznota.ACTION_START"
        const val ACTION_STOP = "com.s1trok.voznota.ACTION_STOP"

        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearDraft() {
            _serviceState.update { it.copy(draft = "", partial = "") }
        }

        fun setMessage(msg: String?) {
            _serviceState.update { it.copy(message = msg) }
        }
    }
}
