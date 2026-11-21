package com.micarbiter.mobile.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.micarbiter.mobile.engine.ArbiterUiState
import com.micarbiter.mobile.engine.AudioArbiterEngine
import com.micarbiter.mobile.model.ActiveRecordingApp
import com.micarbiter.mobile.model.ArbitrationMode
import com.micarbiter.mobile.service.RecordingMonitorService

class MainActivity : ComponentActivity() {

    private val serviceEngineState = mutableStateOf<AudioArbiterEngine?>(null)
    private val serviceStatusState = mutableStateOf("Starting service...")
    private var bindRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RecordingMonitorService.LocalBinder
            if (binder == null) {
                serviceEngineState.value = null
                serviceStatusState.value = "Service connection failed."
                return
            }

            serviceEngineState.value = binder.getEngine()
            serviceStatusState.value = "Connected to monitoring service."
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceEngineState.value = null
            serviceStatusState.value = "Service disconnected. Waiting to reconnect..."
        }

        override fun onBindingDied(name: ComponentName?) {
            serviceEngineState.value = null
            serviceStatusState.value = "Service binding ended. Please reopen MicArbiter."
            if (bindRequested) {
                unbindService(this)
                bindRequested = false
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            serviceEngineState.value = null
            serviceStatusState.value = "The service did not provide a connection."
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startMonitorService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val engine = serviceEngineState.value

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    if (engine == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = serviceStatusState.value,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        val uiState by engine.state.collectAsState()

                        MicArbiterScreen(
                            state = uiState,
                            onSelectMode = engine::setMode,
                            onFocusApp = engine::focusApp,
                            onPttChange = engine::setPttPressed,
                            onToggleTalk = engine::toggleTalk,
                            onPanicToggle = {
                                engine.setPanicMute(!uiState.panicMuted)
                            },
                            onDemoToggle = {
                                engine.setDemoMode(!uiState.demoMode)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startMonitorService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startMonitorService() {
        if (bindRequested) return

        val intent = Intent(this, RecordingMonitorService::class.java)

        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            serviceStatusState.value = "Unable to start service: ${e.message ?: "unknown error"}"
            return
        }

        serviceStatusState.value = "Starting service..."
        bindRequested = bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        if (!bindRequested) {
            serviceStatusState.value = "Unable to connect to the monitoring service."
        }
    }

    override fun onDestroy() {
        if (bindRequested) {
            unbindService(serviceConnection)
            bindRequested = false
        }
        serviceEngineState.value = null
        super.onDestroy()
    }
}

@Composable
fun MicArbiterScreen(
    state: ArbiterUiState,
    onSelectMode: (ArbitrationMode) -> Unit,
    onFocusApp: (String) -> Unit,
    onPttChange: (Boolean) -> Unit,
    onToggleTalk: () -> Unit,
    onPanicToggle: () -> Unit,
    onDemoToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val statusText = when {
                        state.panicMuted -> "PANIC MUTED"
                        state.micMuted -> "MIC MUTED"
                        else -> "MIC LIVE"
                    }
                    val badgeColor = if (state.micMuted || state.panicMuted) Color(0xFFEF4444) else Color(0xFF10B981)
                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    val targetName = state.activeApps.find { it.appId == state.activeTargetAppId }?.displayName ?: "None"
                    Text("Target: $targetName", color = Color(0xFFF8FAFC), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onPanicToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.panicMuted) Color(0xFFF59E0B) else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (state.panicMuted) "UNMUTE" else "PANIC MUTE", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Mode & Demo Row
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Arbitration Mode", color = Color.White, fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = state.demoMode,
                        onClick = onDemoToggle,
                        label = { Text(if (state.demoMode) "Demo: ON" else "Demo: OFF") }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArbitrationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.label, fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onPttChange(true)
                                        tryAwaitRelease()
                                        onPttChange(false)
                                    }
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("HOLD TO TALK", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(onClick = onToggleTalk, modifier = Modifier.weight(1f)) {
                        Text("Toggle Mic")
                    }
                }
            }
        }

        // Active AI Voice Apps List
        Text("Active AI Voice Apps", color = Color.White, fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.activeApps.isEmpty()) {
                item {
                    Text(
                        "No active voice sessions detected. Toggle 'Demo: ON' above to simulate ChatGPT, Gemini, and Claude.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                items(state.activeApps, key = { it.appId }) { app ->
                    ActiveAppRow(app = app, onFocus = { onFocusApp(app.appId) })
                }
            }
        }
    }
}

@Composable
fun ActiveAppRow(app: ActiveRecordingApp, onFocus: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF26334A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#${app.priority}  ${app.displayName}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    if (app.isMuted) "MUTED" else "LIVE",
                    color = if (app.isMuted) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onFocus,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (app.isActiveTarget) Color(0xFF10B981) else Color(0xFF3B82F6)
                )
            ) {
                Text(if (app.isActiveTarget) "ACTIVE" else "Focus Mic")
            }
        }
    }
}
