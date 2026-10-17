package io.github.yarkoody.scalesync.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.ai.ChatGptAuth
import io.github.yarkoody.scalesync.ai.ChatGptClient
import io.github.yarkoody.scalesync.ai.ChatMessage
import io.github.yarkoody.scalesync.ai.ChatModel
import io.github.yarkoody.scalesync.insights.Assistant
import io.github.yarkoody.scalesync.insights.AssistantShare
import io.github.yarkoody.scalesync.insights.Coach
import io.github.yarkoody.scalesync.insights.HealthBrief
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AskAiScreen(padding: PaddingValues, state: HubState, coach: Coach, onCoach: (Coach) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    var wearables by rememberSaveable { mutableStateOf(true) }
    var medical by rememberSaveable { mutableStateOf(true) }
    var preview by remember { mutableStateOf(false) }
    val assistant = state.assistant
    val installed = remember(assistant) { AssistantShare.isInstalled(context, assistant) }
    val auth by ChatGptAuth.state.collectAsState()
    val brief = remember(coach, question, wearables, medical, state.wellness, state.medical) {
        HealthBrief.build(coach, question, state.wellness, state.medical, LocalDate.now(), wearables, medical)
    }
    val chatReady = assistant == Assistant.CHATGPT && (auth as? ChatGptAuth.State.SignedIn)?.planAllowed == true

    // In-app conversation (ChatGPT plan only)
    val chat = remember { mutableStateListOf<ChatMessage>() }
    var streaming by remember { mutableStateOf<String?>(null) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var followUp by rememberSaveable { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var models by remember { mutableStateOf(emptyList<ChatModel>()) }
    var model by remember { mutableStateOf(chatModel(context)) }

    LaunchedEffect(chatReady) {
        if (chatReady && models.isEmpty()) {
            models = runCatching { ChatGptClient.models(context) }.getOrDefault(emptyList())
            if (model == null || models.none { it.slug == model }) model = models.firstOrNull()?.slug
        }
    }

    fun send(text: String) {
        val m = model ?: run { chatError = "No ChatGPT model is available on this plan."; return }
        chat.add(ChatMessage(true, text))
        chatError = null
        streaming = ""
        job = scope.launch {
            try {
                ChatGptClient.reply(context, m, chat.toList()).collect { streaming = (streaming ?: "") + it }
                chat.add(ChatMessage(false, streaming.orEmpty()))
            } catch (e: Exception) {
                chatError = e.message
                if (chat.lastOrNull()?.fromUser == true) chat.removeAt(chat.lastIndex)
            } finally {
                streaming = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Ask your AI", "Your data, read by the AI you already pay for") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Assistant.entries) { a ->
                    FilterChip(selected = a == assistant, onClick = { state.selectAssistant(a) }, label = { Text(a.label) })
                }
            }
        }

        if (assistant == Assistant.CHATGPT) item { ChatGptConnectCard(auth, context) }

        item { SectionTitle("Who should look at it?") }
        items(Coach.entries) { c ->
            val selected = c == coach
            Card(
                Modifier.fillMaxWidth().clickable { onCoach(c) },
                colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else CardDefaults.cardColors(),
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(c.blurb, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Anything specific? (optional)") }, minLines = 2)
        }

        item {
            Column {
                ToggleRow("Fitbit, sleep and scale data (30 days)", wearables) { wearables = it }
                ToggleRow("Medical record (labs, medications, history)", medical) { medical = it }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (chatReady) {
                    Button(onClick = { chat.clear(); send(brief) }, enabled = streaming == null) { Text("Ask here") }
                    OutlinedButton(onClick = { AssistantShare.sendText(context, assistant, brief) }) { Text("Open app") }
                } else {
                    Button(onClick = { AssistantShare.sendText(context, assistant, brief) }) {
                        Text(if (installed) "Open in ${assistant.label}" else "Share…")
                    }
                }
                TextButton(onClick = {
                    AssistantShare.copy(context, brief)
                    Toast.makeText(context, "Copied. Paste it into any AI chat.", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = {
                    AssistantShare.sendFile(context, assistant, AssistantShare.briefFile(context, brief), "text/markdown", coach.ask)
                }) { Text("As file") }
            }
        }

        if (chatReady && models.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(models) { m ->
                    FilterChip(selected = m.slug == model, onClick = { model = m.slug; saveChatModel(context, m.slug) },
                        label = { Text(m.name, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }

        // The conversation
        items(chat.drop(1)) { m -> Bubble(m) } // the first message is the brief itself
        streaming?.let { s -> item { Bubble(ChatMessage(false, s.ifEmpty { "Thinking…" })) } }
        chatError?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (chatReady && chat.size >= 2 && streaming == null) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(followUp, { followUp = it }, modifier = Modifier.weight(1f), label = { Text("Ask a follow-up") })
                Button(onClick = { val t = followUp.trim(); followUp = ""; send(t) }, enabled = followUp.isNotBlank()) { Text("Send") }
            }
        }
        if (streaming != null) item { TextButton(onClick = { job?.cancel() }) { Text("Stop") } }

        item {
            Text(
                if (chatReady) "\"Ask here\" sends this data to ChatGPT on your plan (OpenAI doesn't store it for this). " +
                    "Nothing is sent until you tap a button. Your name and ID number are never in this data."
                else "Nothing leaves your phone until you tap one of these, and then it goes only to the app you pick, " +
                    "on your ${assistant.plan} plan. Your name and ID number are never in this data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!chatReady) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tip: keep one place for your health chats", style = MaterialTheme.typography.titleSmall)
                    Text("In ${assistant.label}, make a project (or Gem, or Space) called \"My health\" and start these " +
                        "chats in it, so it remembers earlier check-ins. \"As file\" gives you the data as a file to add to it.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            TextButton(onClick = { preview = !preview }) {
                Text(if (preview) "Hide what will be sent" else "Show what will be sent (${brief.length} characters)")
            }
        }
        if (preview) item {
            Card(Modifier.fillMaxWidth()) {
                Text(brief, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChatGptConnectCard(auth: ChatGptAuth.State, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (auth) {
                is ChatGptAuth.State.SignedIn -> {
                    Text("Connected to ChatGPT" + if (auth.email.isNotEmpty()) " as ${auth.email}" else "",
                        style = MaterialTheme.typography.titleSmall)
                    Text(if (auth.planAllowed) "Answers appear right here and count against your ChatGPT plan."
                        else "You didn't allow using your plan when signing in. Connect again and allow it.",
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (!auth.planAllowed) TextButton(onClick = { ChatGptAuth.signIn(context) }) { Text("Connect again") }
                        TextButton(onClick = { ChatGptAuth.signOut(context) }) { Text("Disconnect") }
                    }
                }
                ChatGptAuth.State.Waiting -> {
                    Text("Finish signing in in your browser…", style = MaterialTheme.typography.titleSmall)
                    Text("When it says Connected, come back here.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Use your ChatGPT plan inside this app", style = MaterialTheme.typography.titleSmall)
                    Text("Sign in with ChatGPT (Plus or Pro) and answers come back here, with follow-up questions. " +
                        "No API key, no extra cost. Without it, the button below opens the ChatGPT app instead.",
                        style = MaterialTheme.typography.bodySmall)
                    (auth as? ChatGptAuth.State.Failed)?.let {
                        Text("Last try failed: ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { ChatGptAuth.signIn(context) }) { Text("Sign in with ChatGPT") }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Card(
        Modifier.fillMaxWidth().padding(start = if (m.fromUser) 40.dp else 0.dp, end = if (m.fromUser) 0.dp else 16.dp),
        colors = if (m.fromUser) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Text(m.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun chatModel(context: Context): String? =
    context.getSharedPreferences("assistant", Context.MODE_PRIVATE).getString("chatgpt_model", null)

private fun saveChatModel(context: Context, slug: String) =
    context.getSharedPreferences("assistant", Context.MODE_PRIVATE).edit().putString("chatgpt_model", slug).apply()
