package com.micarbiter.mobile.engine

import com.micarbiter.mobile.model.ActiveRecordingApp
import com.micarbiter.mobile.model.ArbitrationMode
import com.micarbiter.mobile.model.DefaultVoiceAppRegistry
import com.micarbiter.mobile.model.VoiceAppProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SystemMicController {
    fun isMicMuted(): Boolean
    fun setMicMuted(muted: Boolean)
    fun requestExclusiveAudioFocus()
    fun abandonExclusiveAudioFocus()
}

data class ArbiterUiState(
    val mode: ArbitrationMode = ArbitrationMode.EXCLUSIVE_FOCUS,
    val micMuted: Boolean = false,
    val panicMuted: Boolean = false,
    val pttPressed: Boolean = false,
    val toggleLive: Boolean = false,
    val demoMode: Boolean = false,
    val activeTargetAppId: String? = null,
    val activeApps: List<ActiveRecordingApp> = emptyList(),
    val logs: List<String> = emptyList()
)

class AudioArbiterEngine(
    private val micController: SystemMicController,
    private val profiles: List<VoiceAppProfile> = DefaultVoiceAppRegistry.profiles
) {
    private val _state = MutableStateFlow(ArbiterUiState())
    val state: StateFlow<ArbiterUiState> = _state.asStateFlow()

    private val lock = Any()

    fun updateActiveSessions(sessions: List<ActiveRecordingApp>) = synchronized(lock) {
        val current = _state.value
        val sorted = sessions.sortedWith(compareBy({ it.priority }, { it.displayName }))
        val targetId = when {
            current.mode == ArbitrationMode.AUTO_PRIORITY && sorted.isNotEmpty() -> sorted.first().appId
            current.activeTargetAppId != null && sorted.any { it.appId == current.activeTargetAppId } -> current.activeTargetAppId
            else -> sorted.firstOrNull()?.appId
        }
        applyPolicy(current.copy(activeTargetAppId = targetId, activeApps = sorted))
    }

    fun setMode(mode: ArbitrationMode) = synchronized(lock) {
        val current = _state.value
        val nextTarget = if (mode == ArbitrationMode.AUTO_PRIORITY) {
            current.activeApps.minByOrNull { it.priority }?.appId ?: current.activeTargetAppId
        } else {
            current.activeTargetAppId
        }
        applyPolicy(
            current.copy(
                mode = mode,
                activeTargetAppId = nextTarget,
                logs = appendLog(current.logs, "Mode switched to ${mode.label}")
            )
        )
    }

    fun focusApp(appId: String) = synchronized(lock) {
        val current = _state.value
        val name = current.activeApps.find { it.appId == appId }?.displayName ?: appId
        micController.requestExclusiveAudioFocus()
        applyPolicy(
            current.copy(
                activeTargetAppId = appId,
                logs = appendLog(current.logs, "Focused mic on $name")
            )
        )
    }

    fun setPttPressed(pressed: Boolean) = synchronized(lock) {
        val current = _state.value
        if (current.pttPressed == pressed) return@synchronized
        val msg = if (pressed) "PTT LIVE" else "PTT released (muted)"
        applyPolicy(
            current.copy(
                pttPressed = pressed,
                logs = appendLog(current.logs, msg)
            )
        )
    }

    fun toggleTalk() = synchronized(lock) {
        val current = _state.value
        val nextLive = !current.toggleLive
        val msg = if (nextLive) "Mic toggled LIVE" else "Mic toggled MUTED"
        applyPolicy(
            current.copy(
                toggleLive = nextLive,
                logs = appendLog(current.logs, msg)
            )
        )
    }

    fun setPanicMute(muted: Boolean) = synchronized(lock) {
        val current = _state.value
        val msg = if (muted) "PANIC MUTE ENGAGED" else "Panic mute cleared"
        if (muted) {
            micController.requestExclusiveAudioFocus()
        } else {
            micController.abandonExclusiveAudioFocus()
        }
        applyPolicy(
            current.copy(
                panicMuted = muted,
                logs = appendLog(current.logs, msg)
            )
        )
    }

    fun setDemoMode(enabled: Boolean) = synchronized(lock) {
        val current = _state.value
        val demoSessions = if (enabled) {
            listOf(
                ActiveRecordingApp("chatgpt", "ChatGPT Voice", "com.openai.chatgpt", 101, 1, false, true),
                ActiveRecordingApp("gemini", "Google Gemini Live", "com.google.android.apps.bard", 102, 2, true, false),
                ActiveRecordingApp("claude", "Claude", "com.anthropic.claude", 103, 3, true, false)
            )
        } else {
            emptyList()
        }
        applyPolicy(
            current.copy(
                demoMode = enabled,
                activeApps = demoSessions,
                activeTargetAppId = demoSessions.firstOrNull()?.appId,
                logs = appendLog(current.logs, if (enabled) "Demo mode enabled" else "Demo mode disabled")
            )
        )
    }

    fun matchProfile(packageName: String): VoiceAppProfile? {
        val lower = packageName.lowercase()
        return profiles.firstOrNull { prof ->
            prof.enabled && prof.packageHints.any { lower.contains(it.lowercase()) }
        }
    }

    private fun applyPolicy(next: ArbiterUiState) {
        val micLive = when {
            next.panicMuted -> false
            next.mode == ArbitrationMode.PUSH_TO_TALK -> next.pttPressed
            next.mode == ArbitrationMode.TOGGLE_TO_TALK -> next.toggleLive
            else -> true
        }
        micController.setMicMuted(!micLive)

        val updatedApps = next.activeApps.map { app ->
            val isTarget = app.appId == next.activeTargetAppId
            app.copy(
                isActiveTarget = isTarget,
                isMuted = !micLive || !isTarget
            )
        }
        _state.value = next.copy(
            micMuted = !micLive,
            activeApps = updatedApps
        )
    }

    private fun appendLog(logs: List<String>, entry: String): List<String> =
        (logs + entry).takeLast(25)
}
