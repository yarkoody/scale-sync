package io.github.yarkoody.scalesync.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.insights.ClaudeBrief
import io.github.yarkoody.scalesync.insights.ClaudeShare
import io.github.yarkoody.scalesync.insights.Coach
import java.time.LocalDate

@Composable
fun AskClaudeScreen(padding: PaddingValues, state: HubState, coach: Coach, onCoach: (Coach) -> Unit) {
    val context = LocalContext.current
    var question by rememberSaveable { mutableStateOf("") }
    var wearables by rememberSaveable { mutableStateOf(true) }
    var medical by rememberSaveable { mutableStateOf(true) }
    var preview by remember { mutableStateOf(false) }
    val installed = remember { ClaudeShare.isInstalled(context) }
    val brief = remember(coach, question, wearables, medical, state.wellness, state.medical) {
        ClaudeBrief.build(coach, question, state.wellness, state.medical, LocalDate.now(), wearables, medical)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle("Ask Claude", "Your data, read by Claude on your own subscription")
        }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ClaudeShare.sendText(context, brief) }) {
                    Text(if (installed) "Open in Claude" else "Share…")
                }
                OutlinedButton(onClick = {
                    ClaudeShare.copy(context, brief)
                    Toast.makeText(context, "Copied. Paste it into any Claude chat.", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = {
                    ClaudeShare.sendFile(context, ClaudeShare.briefFile(context, brief), "text/markdown", coach.ask)
                }) { Text("As file") }
            }
        }

        item {
            Text(
                "Nothing leaves your phone until you tap one of these, and then it goes only to the app you pick. " +
                    "The Claude app uses your Pro or Max plan. " +
                    (if (installed) "" else "Install the Claude app to send it there directly. ") +
                    "Your name and ID number are never in this data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tip: keep one Claude project for your health", style = MaterialTheme.typography.titleSmall)
                    Text("In the Claude app, make a project called \"My health\" and start these chats in it. " +
                        "Claude then remembers earlier check-ins, and you can add the brief with \"As file\" to the " +
                        "project's knowledge so every chat starts from it.", style = MaterialTheme.typography.bodySmall)
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
