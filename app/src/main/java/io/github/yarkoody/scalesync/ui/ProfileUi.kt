package io.github.yarkoody.scalesync.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.core.Algorithm
import io.github.yarkoody.scalesync.core.Profile
import io.github.yarkoody.scalesync.core.Sex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val BIRTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun Algorithm.title() = when (this) {
    Algorithm.XIAOMI -> "Zepp Life"
    Algorithm.SCIENCE -> "bodymiscale"
}

private fun Algorithm.explanation() = when (this) {
    Algorithm.XIAOMI ->
        "The formulas Xiaomi's own Zepp Life (Mi Fit) app uses, reverse-engineered by openScale. " +
            "Pick this if you want the same numbers Zepp Life showed you. " +
            "Muscle here means skeletal muscle, the muscle you train."
    Algorithm.SCIENCE ->
        "Formulas from published research (Siri for body fat, Schofield for BMR), from the " +
            "bodymiscale project. Pick this for numbers grounded in studies rather than Xiaomi's app. " +
            "It corrects lean mass for women, and muscle here means all lean soft tissue, so it reads higher."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCard(initial: Profile?, onSave: (Profile) -> Unit) {
    var height by remember { mutableStateOf(initial?.heightCm?.toInt()?.toString() ?: "") }
    var birth by remember { mutableStateOf(initial?.birthDate) }
    var sex by remember { mutableStateOf(initial?.sex ?: Sex.MALE) }
    var algorithm by remember { mutableStateOf(initial?.algorithm ?: Algorithm.XIAOMI) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    if (pickingDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birth?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            initialDisplayedMonthMillis = (birth ?: today.minusYears(30))
                .withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = 1920..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        birth = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state, title = { Text("Birth date", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your profile", style = MaterialTheme.typography.titleMedium)
            Text("Body composition is calculated from your weight and impedance plus your height, age and sex.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = height, onValueChange = { height = it.filter(Char::isDigit).take(3) },
                label = { Text("Height") }, suffix = { Text("cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            // Read-only field that opens the calendar on tap.
            val dateTaps = remember { MutableInteractionSource() }
            LaunchedEffect(dateTaps) {
                dateTaps.interactions.collect { if (it is PressInteraction.Release) pickingDate = true }
            }
            OutlinedTextField(
                value = birth?.format(BIRTH_FORMAT) ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Birth date") },
                placeholder = { Text("dd/mm/yyyy") },
                trailingIcon = { Text("📅", Modifier.padding(end = 12.dp)) },
                interactionSource = dateTaps,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sex == Sex.MALE, { sex = Sex.MALE }, label = { Text("Male") })
                FilterChip(sex == Sex.FEMALE, { sex = Sex.FEMALE }, label = { Text("Female") })
            }

            Text("Body composition formulas", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Algorithm.entries.forEach { a ->
                    FilterChip(algorithm == a, { algorithm = a }, label = { Text(a.title()) })
                }
            }
            AnimatedContent(targetState = algorithm, label = "formula-explanation") { a ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        a.explanation(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val h = height.toFloatOrNull()
                    val b = birth
                    when {
                        h == null || h !in 50f..250f -> error = "Enter your height in cm."
                        b == null -> error = "Pick your birth date."
                        else -> onSave(Profile(h, b, sex, algorithm))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
