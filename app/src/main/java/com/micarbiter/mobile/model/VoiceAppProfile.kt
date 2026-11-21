package com.micarbiter.mobile.model

enum class ArbitrationMode(val label: String) {
    EXCLUSIVE_FOCUS("Exclusive Focus"),
    PUSH_TO_TALK("Push-to-Talk"),
    TOGGLE_TO_TALK("Toggle-to-Talk"),
    AUTO_PRIORITY("Auto-Priority")
}

data class VoiceAppProfile(
    val appId: String,
    val displayName: String,
    val packageHints: List<String>,
    val priority: Int,
    val enabled: Boolean = true
)

data class ActiveRecordingApp(
    val appId: String,
    val displayName: String,
    val packageName: String,
    val sessionId: Int,
    val priority: Int,
    val isMuted: Boolean,
    val isActiveTarget: Boolean
)

object DefaultVoiceAppRegistry {
    val profiles: List<VoiceAppProfile> = listOf(
        VoiceAppProfile("chatgpt", "ChatGPT Voice", listOf("com.openai.chatgpt"), priority = 1),
        VoiceAppProfile("gemini", "Google Gemini Live", listOf("com.google.android.apps.bard", "com.google.android.googlequicksearchbox"), priority = 2),
        VoiceAppProfile("claude", "Claude", listOf("com.anthropic.claude"), priority = 3),
        VoiceAppProfile("copilot", "Microsoft Copilot", listOf("com.microsoft.copilot"), priority = 4),
        VoiceAppProfile("perplexity", "Perplexity", listOf("ai.perplexity.app.android"), priority = 5),
        VoiceAppProfile("discord", "Discord", listOf("com.discord"), priority = 6),
        VoiceAppProfile("zoom", "Zoom", listOf("us.zoom.videomeetings"), priority = 7)
    )
}
