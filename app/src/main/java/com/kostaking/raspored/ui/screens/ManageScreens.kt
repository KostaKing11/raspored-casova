@file:OptIn(ExperimentalMaterial3Api::class)

package com.kostaking.raspored.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.data.DayOffEntity
import com.kostaking.raspored.data.PeriodEntity
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.data.SubjectEntity
import com.kostaking.raspored.ui.components.ColorPicker
import com.kostaking.raspored.ui.components.ConfirmDialog
import com.kostaking.raspored.ui.components.EmptyState
import com.kostaking.raspored.ui.components.GlassCard
import com.kostaking.raspored.ui.components.Palette
import com.kostaking.raspored.ui.components.SubScreen
import com.kostaking.raspored.ui.components.TimePickerDialog
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.contentOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------- Termini časova ----------------

@Composable
fun PeriodsScreen(
    state: ScheduleState,
    onBack: () -> Unit,
    onSave: (PeriodEntity) -> Unit,
    onAdd: () -> Unit,
    onRemoveLast: () -> Unit,
) {
    var picking by remember { mutableStateOf<Pair<PeriodEntity, Boolean>?>(null) } // (period, isStart)
    var confirmRemove by remember { mutableStateOf(false) }
    SubScreen(
        title = "Termini časova",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Dodaj čas") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Tapni početak ili kraj da promeniš vreme. Časovi mogu da se dodiruju (npr. 1. i 2. čas u 09:15).",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.textMuted,
                )
            }
            itemsIndexed(state.periods, key = { _, p -> p.idx }) { i, p ->
                val prev = state.periods.getOrNull(i - 1)
                val overlap = prev != null && p.startMin < prev.endMin
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.idx}. čas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(72.dp))
                        TextButton(onClick = { picking = p to true }, modifier = Modifier.testTag("start-${p.idx}")) {
                            Text(Format.minutesOfDay(p.startMin), style = MaterialTheme.typography.titleLarge)
                        }
                        Text("–")
                        TextButton(onClick = { picking = p to false }, modifier = Modifier.testTag("end-${p.idx}")) {
                            Text(Format.minutesOfDay(p.endMin), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${p.endMin - p.startMin} min", color = LocalExtraColors.current.textMuted)
                        if (i == state.periods.lastIndex) {
                            IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Rounded.Delete, "Ukloni poslednji čas") }
                        }
                    }
                    if (overlap) {
                        Text("Preklapa se sa prethodnim časom", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    picking?.let { (p, isStart) ->
        TimePickerDialog(
            title = if (isStart) "Početak ${p.idx}. časa" else "Kraj ${p.idx}. časa",
            initialMinutes = if (isStart) p.startMin else p.endMin,
            onDismiss = { picking = null },
            onConfirm = { m ->
                onSave(if (isStart) p.copy(startMin = m) else p.copy(endMin = m))
                picking = null
            },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Ukloniti poslednji čas?",
            text = "Časovi u tom terminu biće obrisani (blokovi skraćeni).",
            confirm = "Ukloni",
            onDismiss = { confirmRemove = false },
            onConfirm = onRemoveLast,
        )
    }
}

// ---------------- Predmeti i boje ----------------

@Composable
fun SubjectsScreen(
    state: ScheduleState,
    onBack: () -> Unit,
    onSave: (SubjectEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<SubjectEntity?>(null) }
    SubScreen(
        title = "Predmeti i boje",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = SubjectEntity(name = "", color = Palette.colors.first().toInt()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Novi predmet") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.subjects, key = { it.id }) { s ->
                val color = Color(s.color)
                val uses = state.lessons.count { it.subjectId == s.id }
                GlassCard(onClick = { editing = s }, modifier = Modifier.testTag("subject-${s.name}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            Text(s.name.take(1), color = contentOn(color), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (uses == 0) "nije u rasporedu" else "$uses u rasporedu nedeljno",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalExtraColors.current.textMuted,
                            )
                        }
                        IconButton(onClick = { onDelete(s.id) }) { Icon(Icons.Rounded.Delete, "Obriši") }
                    }
                }
            }
        }
    }
    editing?.let { s ->
        var name by remember(s) { mutableStateOf(s.name) }
        var color by remember(s) { mutableStateOf(Color(s.color)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp),
            title = { Text(if (s.id == 0L) "Novi predmet" else s.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Naziv") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("subjectName"),
                    )
                    Spacer(Modifier.height(16.dp))
                    ColorPicker(selected = color, onSelect = { color = it })
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(s.copy(name = name, color = color.toArgb())); editing = null }, modifier = Modifier.testTag("saveSubject")) {
                    Text("Sačuvaj")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Otkaži") } },
        )
    }
}

// ---------------- Neradni dani ----------------

@Composable
fun DaysOffScreen(
    state: ScheduleState,
    today: LocalDate,
    onBack: () -> Unit,
    onSave: (DayOffEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    SubScreen(
        title = "Neradni dani",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Dodaj") },
                modifier = Modifier.testTag("addDayOff"),
            )
        },
    ) { padding ->
        if (state.daysOff.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Rounded.BeachAccess, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.tertiary) },
                title = "Nema neradnih dana",
                text = "Dodaj praznike ili raspust – tih dana nema notifikacija o časovima.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.daysOff, key = { it.id }) { d ->
                    val start = LocalDate.ofEpochDay(d.startDate)
                    val end = LocalDate.ofEpochDay(d.endDate)
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.label.ifBlank { "Neradno" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (start == end) Format.fullDate(start) else "${Format.fullDate(start)} – ${Format.fullDate(end)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (end.isBefore(today)) LocalExtraColors.current.textMuted else MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            IconButton(onClick = { onDelete(d.id) }) { Icon(Icons.Rounded.Delete, "Obriši") }
                        }
                    }
                }
            }
        }
    }
    if (adding) DayOffDialog(today, onDismiss = { adding = false }, onSave = { onSave(it); adding = false })
}

@Composable
fun DayOffDialog(initial: LocalDate, onDismiss: () -> Unit, onSave: (DayOffEntity) -> Unit) {
    val utc = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val range = rememberDateRangePickerState(initialSelectedStartDateMillis = utc, initialSelectedEndDateMillis = null)
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(12.dp),
        title = { Text("Neradni dan ili period") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Naziv (npr. Zimski raspust)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dayOffLabel"),
                )
                Spacer(Modifier.height(8.dp))
                Text("Izaberi jedan dan ili početak i kraj perioda:", style = MaterialTheme.typography.bodySmall)
                DateRangePicker(state = range, modifier = Modifier.height(420.dp), title = null, headline = null, showModeToggle = false)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = range.selectedStartDateMillis ?: return@TextButton
                    val e = range.selectedEndDateMillis ?: s
                    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    onSave(DayOffEntity(startDate = day(s), endDate = day(e), label = label.trim()))
                },
                modifier = Modifier.testTag("saveDayOff"),
            ) { Text("Sačuvaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži") } },
    )
}

