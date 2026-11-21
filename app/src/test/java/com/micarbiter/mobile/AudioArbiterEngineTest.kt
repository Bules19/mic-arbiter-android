package com.micarbiter.mobile.engine

import com.micarbiter.mobile.model.ArbitrationMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioArbiterEngineTest {

    private class FakeMicController : SystemMicController {
        var muted = false
        var exclusiveRequests = 0
        override fun isMicMuted(): Boolean = muted
        override fun setMicMuted(muted: Boolean) { this.muted = muted }
        override fun requestExclusiveAudioFocus() { exclusiveRequests++ }
        override fun abandonExclusiveAudioFocus() {}
    }

    private lateinit var mic: FakeMicController
    private lateinit var engine: AudioArbiterEngine

    @Before
    fun setup() {
        mic = FakeMicController()
        engine = AudioArbiterEngine(mic)
        engine.setDemoMode(true)
    }

    @Test
    fun exclusiveFocusMutesNonTargetApps() {
        engine.focusApp("gemini")
        val state = engine.state.value
        assertEquals("gemini", state.activeTargetAppId)
        assertFalse(state.micMuted)
        val byId = state.activeApps.associateBy { it.appId }
        assertFalse(byId.getValue("gemini").isMuted)
        assertTrue(byId.getValue("chatgpt").isMuted)
        assertTrue(byId.getValue("claude").isMuted)
    }

    @Test
    fun pushToTalkMutesUntilPressed() {
        engine.setMode(ArbitrationMode.PUSH_TO_TALK)
        assertTrue(engine.state.value.micMuted)
        engine.setPttPressed(true)
        assertFalse(engine.state.value.micMuted)
        engine.setPttPressed(false)
        assertTrue(engine.state.value.micMuted)
    }

    @Test
    fun panicMuteOverridesAllModes() {
        engine.focusApp("chatgpt")
        engine.setPanicMute(true)
        assertTrue(engine.state.value.panicMuted)
        assertTrue(engine.state.value.micMuted)
        assertTrue(engine.state.value.activeApps.all { it.isMuted })

        engine.setPanicMute(false)
        assertFalse(engine.state.value.panicMuted)
        assertFalse(engine.state.value.micMuted)
    }
}
