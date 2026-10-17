package io.github.yarkoody.scalesync.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import io.github.yarkoody.scalesync.health.HealthConnectReader
import io.github.yarkoody.scalesync.health.HealthConnectWriter
import io.github.yarkoody.scalesync.ai.ChatGptAuth
import io.github.yarkoody.scalesync.insights.Assistant
import io.github.yarkoody.scalesync.insights.AssistantShare
import io.github.yarkoody.scalesync.insights.Coach
import io.github.yarkoody.scalesync.medical.Document
import io.github.yarkoody.scalesync.medical.MedicalRecord
import io.github.yarkoody.scalesync.medical.MedicalStore
import io.github.yarkoody.scalesync.wellness.Wellness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: String) {
    TODAY("Today", "☀"),
    SLEEP("Sleep", "☾"),
    BODY("Body", "⚖"),
    RECORDS("Records", "✚"),
    AI("Ask AI", "✦"),
}

/** Data shared by the tabs: Health Connect readings, the medical record and uploaded documents. */
@Stable
class HubState(private val app: Context) {
    val medicalStore = MedicalStore(app)
    var wellness by mutableStateOf(Wellness())
    var loading by mutableStateOf(false)
    var hcAvailable by mutableStateOf(HealthConnectWriter.isAvailable(app))
    var readGranted by mutableStateOf(false)
    var medical by mutableStateOf(MedicalRecord())
    var documents by mutableStateOf(emptyList<Document>())
    var assistant by mutableStateOf(AssistantShare.selected(app))
        private set

    init {
        ChatGptAuth.init(app)
    }

    fun selectAssistant(a: Assistant) {
        AssistantShare.select(app, a)
        assistant = a
    }

    suspend fun refreshWellness() {
        loading = true
        try {
            hcAvailable = HealthConnectWriter.isAvailable(app)
            val granted = runCatching { HealthConnectWriter.grantedPermissions(app) }.getOrDefault(emptySet())
            readGranted = granted.any { it in HealthConnectReader.PERMISSIONS }
            wellness = withContext(Dispatchers.IO) { runCatching { HealthConnectReader.read(app) }.getOrDefault(Wellness()) }
        } finally {
            loading = false
        }
    }

    suspend fun refreshMedical() {
        val (m, d) = withContext(Dispatchers.IO) { medicalStore.load() to medicalStore.documents() }
        medical = m
        documents = d
    }
}

@Composable
fun HomeApp() {
    val context = LocalContext.current
    val state = remember { HubState(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var coach by rememberSaveable { mutableStateOf(Coach.CHECK_IN) }

    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { state.refreshWellness() }
    }
    val connect = { hcLauncher.launch(HealthConnectReader.PERMISSIONS + HealthConnectWriter.PERMISSIONS) }
    val ask = { c: Coach -> coach = c; tab = Tab.AI }

    LaunchedEffect(Unit) {
        state.refreshMedical()
        state.refreshWellness()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            if (t == Tab.TODAY || t == Tab.SLEEP) scope.launch { state.refreshWellness() }
                        },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.TODAY -> TodayScreen(padding, state, connect, ask) { tab = Tab.RECORDS }
            Tab.SLEEP -> SleepScreen(padding, state, connect, ask)
            Tab.BODY -> BodyScreen(padding)
            Tab.RECORDS -> RecordsScreen(padding, state, ask)
            Tab.AI -> AskAiScreen(padding, state, coach) { coach = it }
        }
    }
}
