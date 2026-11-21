package com.micarbiter.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.micarbiter.mobile.engine.AudioArbiterEngine
import com.micarbiter.mobile.engine.SystemMicController
import com.micarbiter.mobile.model.ActiveRecordingApp
import com.micarbiter.mobile.ui.MainActivity

class RecordingMonitorService : Service(), SystemMicController {

    companion object {
        const val CHANNEL_ID = "mic_arbiter_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PANIC_TOGGLE = "com.micarbiter.mobile.ACTION_PANIC_TOGGLE"

        @Volatile
        var sharedEngine: AudioArbiterEngine? = null
            private set
    }

    inner class LocalBinder : Binder() {
        fun getEngine(): AudioArbiterEngine = engine
    }

    private val binder = LocalBinder()

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private lateinit var engine: AudioArbiterEngine

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            val active = (configs ?: emptyList()).mapIndexedNotNull { idx, cfg ->
                val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // clientAudioSessionId is always available; package lookup requires privileged perm on newer Android
                    "session.${cfg.clientAudioSessionId}"
                } else {
                    "session.${cfg.clientAudioSessionId}"
                }
                val matched = engine.matchProfile(pkg)
                ActiveRecordingApp(
                    appId = matched?.appId ?: "session_${cfg.clientAudioSessionId}",
                    displayName = matched?.displayName ?: "Voice Session #${idx + 1}",
                    packageName = pkg,
                    sessionId = cfg.clientAudioSessionId,
                    priority = matched?.priority ?: (50 + idx),
                    isMuted = false,
                    isActiveTarget = false
                )
            }
            if (!engine.state.value.demoMode) {
                engine.updateActiveSessions(active)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        engine = AudioArbiterEngine(this)
        sharedEngine = engine
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring active AI voice sessions"))
        audioManager.registerAudioRecordingCallback(recordingCallback, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PANIC_TOGGLE) {
            val currentlyPanic = engine.state.value.panicMuted
            engine.setPanicMute(!currentlyPanic)
            val status = if (!currentlyPanic) "PANIC MUTE ACTIVE" else "MicArbiter monitoring"
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(status))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        abandonExclusiveAudioFocus()
        sharedEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun isMicMuted(): Boolean = audioManager.isMicrophoneMute

    override fun setMicMuted(muted: Boolean) {
        audioManager.isMicrophoneMute = muted
    }

    override fun requestExclusiveAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()

        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    override fun abandonExclusiveAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MicArbiter Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val panicIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingMonitorService::class.java).apply { action = ACTION_PANIC_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MicArbiter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Panic Mute / Unmute",
                    panicIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }
}
