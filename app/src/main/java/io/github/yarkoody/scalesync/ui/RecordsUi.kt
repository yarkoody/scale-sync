package io.github.yarkoody.scalesync.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.yarkoody.scalesync.insights.AssistantShare
import io.github.yarkoody.scalesync.insights.HealthBrief
import io.github.yarkoody.scalesync.insights.Coach
import io.github.yarkoody.scalesync.medical.Document
import io.github.yarkoody.scalesync.medical.Flag
import io.github.yarkoody.scalesync.medical.LabResult
import io.github.yarkoody.scalesync.medical.MedicalRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

private enum class Section(val label: String) { OVERVIEW("Overview"), LABS("Blood tests"), MEDS("Medications"), HISTORY("History"), DOCS("Documents") }

@Composable
fun RecordsScreen(padding: PaddingValues, state: HubState, onAsk: (Coach) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = state.medicalStore
    val m = state.medical
    val today = LocalDate.now()
    var section by rememberSaveable { mutableStateOf(Section.OVERVIEW) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pendingDoc by remember { mutableStateOf<Uri?>(null) }
    val ai = state.assistant.label

    fun importText(text: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.importText(text) } }
            result.onSuccess { (_, n) ->
                Toast.makeText(context, if (n == 0) "Nothing to import in that text" else "Imported $n entries", Toast.LENGTH_LONG).show()
                state.refreshMedical()
            }.onFailure {
                Toast.makeText(context, "Couldn't read that: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { store.readUri(uri) }.getOrDefault("") }
            importText(text)
        }
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingDoc = uri }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Records", "From your HMO's documents. Stored only on this phone.") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Section.entries) { s ->
                    FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.label) })
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickDoc.launch(arrayOf("application/pdf", "image/*")) }) { Text("Add document") }
                OutlinedButton(onClick = { pasteOpen = true }) { Text("Paste from $ai") }
            }
        }

        if (m.isEmpty && section != Section.DOCS) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How records get in", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Meuhedet, Clalit and Maccabi have no patient API, so records come from the PDFs you download " +
                            "from their app or site.\n\n" +
                            "1. Add document: pick the PDF. It's kept here.\n" +
                            "2. Send it to $ai: the $ai app reads it on your subscription and replies with a data block.\n" +
                            "3. Copy the reply and tap Paste from $ai.\n\n" +
                            "Already have a data file (health-record.json)? Import it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Text("Import data file")
                    }
                }
            }
        }

        when (section) {
            Section.OVERVIEW -> if (!m.isEmpty) {
                val flagged = m.latestLabs().filter { it.first.flag == Flag.HIGH || it.first.flag == Flag.LOW }
                item { SectionTitle("Coming up and open") }
                items(m.dueItems()) { DueRow(it, today) }
                if (flagged.isNotEmpty()) {
                    item { SectionTitle("Latest blood tests outside the range") }
                    items(flagged) { (latest, prev) -> LabRow(latest, prev, m) }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAsk(Coach.LABS) }) { Text("Review labs with $ai") }
                        OutlinedButton(onClick = { onAsk(Coach.VISIT) }) { Text("Visit prep") }
                    }
                }
            }

            Section.LABS -> {
                val dates = m.labDates()
                if (dates.isNotEmpty()) item {
                    Text("${m.labs.size} results from ${dates.size} blood tests, ${dates.last().year}–${dates.first().year}. Tap a test for its history.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                m.latestLabs().groupBy { it.first.group.ifEmpty { "Other" } }.forEach { (group, rows) ->
                    item(key = "g-$group") { SectionTitle(group) }
                    items(rows, key = { "l-${it.first.test}" }) { (latest, prev) -> LabRow(latest, prev, m) }
                }
            }

            Section.MEDS -> items(m.medications) { med ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(med.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(med.generic, med.use).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        Text("${med.dose}. ${med.kind}.", style = MaterialTheme.typography.bodySmall)
                        med.lastPrescribed?.let {
                            Text("Last prescribed ${it.format(DATE)}" + (med.timesPrescribed?.let { n -> ", $n times in total" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Section.HISTORY -> {
                items(m.history.sortedByDescending { it.date }) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.width(92.dp)) {
                            Text(e.date.format(DateTimeFormatter.ofPattern("MMM yyyy")), style = MaterialTheme.typography.labelMedium)
                            Text(e.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(e.item, style = MaterialTheme.typography.titleSmall)
                            if (e.detail.isNotEmpty()) Text(e.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (m.vitals.isNotEmpty()) {
                    item { SectionTitle("Clinic vitals") }
                    items(m.vitals.sortedByDescending { it.date }) { v ->
                        Text("${v.date.format(DATE)}: blood pressure ${v.systolic ?: "?"}/${v.diastolic ?: "?"}, pulse ${v.pulse ?: "?"}" +
                            (v.weightKg?.let { ", ${num(it)} kg" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Section.DOCS -> {
                val docs = state.documents
                if (docs.isEmpty()) item {
                    Text("No documents yet. Add the PDFs you download from your HMO: lab results, medical summaries, " +
                        "prescriptions, imaging reports and letters.", style = MaterialTheme.typography.bodyMedium)
                }
                items(docs, key = { it.id }) { doc ->
                    DocumentRow(doc, ai,
                        onOpen = { openDoc(context, store.fileOf(doc), doc.mime) },
                        onSend = {
                            AssistantShare.sendFile(context, state.assistant, store.fileOf(doc), doc.mime, HealthBrief.EXTRACT_PROMPT)
                            scope.launch {
                                withContext(Dispatchers.IO) { store.update(doc.copy(imported = true)) }
                                state.refreshMedical()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.delete(doc) }
                                state.refreshMedical()
                            }
                        })
                }
                item {
                    TextButton(onClick = { importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Text("Import a data file instead")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (pasteOpen) PasteDialog(ai, onDismiss = { pasteOpen = false }) { text -> pasteOpen = false; importText(text) }

    pendingDoc?.let { uri ->
        AddDocumentDialog(onDismiss = { pendingDoc = null }) { kind, date ->
            pendingDoc = null
            scope.launch {
                val doc = withContext(Dispatchers.IO) { runCatching { store.addDocument(uri, kind, date) }.getOrNull() }
                state.refreshMedical()
                section = Section.DOCS
                if (doc == null) Toast.makeText(context, "Couldn't copy that file", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "Saved. Tap Send to $ai to read it into your record.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun LabRow(latest: LabResult, previous: LabResult?, m: MedicalRecord) {
    var open by remember { mutableStateOf(false) }
    val flagColor = when (latest.flag) {
        Flag.HIGH, Flag.LOW -> WatchColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(latest.test, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (latest.range.isNotEmpty()) "Range ${latest.range}" else "No range on the report") +
                            " · ${latest.date.format(DATE)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${latest.shown} ${latest.unit}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = flagColor)
                    when (latest.flag) {
                        Flag.HIGH -> Text("High", style = MaterialTheme.typography.labelSmall, color = WatchColor)
                        Flag.LOW -> Text("Low", style = MaterialTheme.typography.labelSmall, color = WatchColor)
                        else -> previous?.let { Text("was ${it.shown}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (open) {
                val hist = m.labHistory(latest.test)
                LabSparkline(hist)
                hist.reversed().forEach {
                    Text("${it.date.format(DATE)}: ${it.shown} ${it.unit}" + if (it.note.isNotEmpty()) " (${it.note})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.flag == Flag.HIGH || it.flag == Flag.LOW) WatchColor else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Values over time, with the reference range shaded. */
@Composable
private fun LabSparkline(hist: List<LabResult>) {
    val values = hist.mapNotNull { it.value }
    if (values.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val low = hist.last().low
    val high = hist.last().high
    Canvas(Modifier.fillMaxWidth().height(60.dp).padding(vertical = 6.dp)) {
        val all = values + listOfNotNull(low, high)
        val min = all.min()
        val max = all.max().let { if (it == min) min + 1 else it }
        fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
        if (low != null && high != null) {
            drawRect(band, Offset(0f, y(high)), androidx.compose.ui.geometry.Size(size.width, y(low) - y(high)))
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            drawCircle(line, 3.dp.toPx(), Offset(x, y(v)))
        }
        drawPath(path, line, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun DocumentRow(doc: Document, ai: String, onOpen: () -> Unit, onSend: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall)
            Text("${doc.kind} · ${doc.date?.format(DATE) ?: ""}" + if (doc.imported) " · sent to AI" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onSend) { Text(if (doc.imported) "Send again" else "Send to $ai") }
                TextButton(onClick = { confirm = true }) { Text("Delete") }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this document?") },
        text = { Text("The file is removed from this phone. What was already imported into your record stays.") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun AddDocumentDialog(onDismiss: () -> Unit, onSave: (String, LocalDate?) -> Unit) {
    var kind by remember { mutableStateOf(Document.KINDS.first()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val parsed = MedicalRecord.parseDate(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What is it?", style = MaterialTheme.typography.labelLarge)
                Document.KINDS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Date on the document") },
                    supportingText = { Text("YYYY-MM-DD or DD/MM/YYYY") }, isError = parsed == null, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(kind, parsed) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasteDialog(ai: String, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(AssistantShare.clipboardText(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste from $ai") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copy $ai's whole reply (the part with the ```json block is enough) and paste it here. " +
                    "New entries are added; ones already here are updated.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("{ \"labs\": [ … ] }") })
                TextButton(onClick = { text = AssistantShare.clipboardText(context) }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun openDoc(context: android.content.Context, file: java.io.File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone opens this file type", Toast.LENGTH_LONG).show()
    }
}
