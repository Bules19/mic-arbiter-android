package com.micarbiter.mobile.service

import android.content.Context
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Android Quick Settings Tile allowing one-tap system microphone mute/unmute
 * from the notification shade while any AI voice app is in the foreground.
 */
class MicQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val engine = RecordingMonitorService.sharedEngine
        if (engine != null) {
            val nextPanic = !engine.state.value.panicMuted
            engine.setPanicMute(nextPanic)
        } else {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isMicrophoneMute = !am.isMicrophoneMute
        }
        syncTileState()
    }

    private fun syncTileState() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isMuted = am.isMicrophoneMute
        qsTile?.apply {
            state = if (isMuted) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            label = if (isMuted) "Mic Muted" else "Mic Live"
            updateTile()
        }
    }
}
