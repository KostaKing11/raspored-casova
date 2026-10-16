@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kostaking.raspored.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.data.EventEntity
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.ui.components.ConfirmDialog
import com.kostaking.raspored.ui.components.DropdownField
import com.kostaking.raspored.ui.components.GlassCard
import com.kostaking.raspored.ui.components.SubScreen
import com.kostaking.raspored.ui.components.SubjectDropdown
import com.kostaking.raspored.ui.theme.LocalExtraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun EventEditorScreen(
    state: ScheduleState,
    eventId: Long?,
    initialDate: LocalDate,
    initialPeriod: Int?,
    initialSubjectId: Long?,
    onSave: (EventEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val existing = remember(eventId) { state.events.firstOrNull { it.id == eventId } }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: EventType.KONTROLNI.name) }
    var subjectId by rememberSaveable { mutableStateOf(existing?.subjectId ?: initialSubjectId) }
    var dateEpoch by rememberSaveable { mutableStateOf(existing?.date ?: initialDate.toEpochDay()) }
    var period by rememberSaveable { mutableStateOf(if (existing != null) existing.periodIndex else initialPeriod) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var reminders by rememberSaveable { mutableStateOf(existing?.reminders ?: true) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val date = LocalDate.ofEpochDay(dateEpoch)
    val blocksThatDay = state.timetable.blocksFor(date)

    // When the subject is chosen and no period is set, suggest the first lesson of that subject that day.
    fun suggestPeriod(sid: Long?) {
        if (period == null && sid != null) {
            period = blocksThatDay.firstOrNull { it.lesson.subjectId == sid }?.firstPeriod
        }
    }

    SubScreen(
        title = if (existing == null) "Nova obaveza" else "Izmena obaveze",
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Obriši") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard {
                Text("Tip", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t.name,
                            onClick = { type = t.name },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("type-${t.name}"),
                        )
                    }
                }
            }
            GlassCard {
                SubjectDropdown(
                    subjects = state.subjects,
                    selectedId = subjectId,
                    onSelect = { subjectId = it.id; suggestPeriod(it.id) },
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = Format.dayTitle(date) + " ${date.year}.",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Datum") },
                        trailingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().padding(top = 8.dp).clickable { showDate = true }.testTag("pickDate"))
                }
                Spacer(Modifier.height(12.dp))
                val periodOptions: List<Int?> = listOf<Int?>(null) + state.periods.map { it.idx }
                DropdownField(
                    label = "Čas (opciono)",
                    value = periodLabel(period, blocksThatDay),
                    options = periodOptions,
                    optionLabel = { periodLabel(it, blocksThatDay) },
                    onSelect = { period = it },
                )
            }
            GlassCard {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Beleška (npr. gradivo)") },
                    modifier = Modifier.fillMaxWidth().testTag("note"),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Podsetnici", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Veče pre i ujutru tog dana (vreme se podešava u Podešavanjima)",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalExtraColors.current.textMuted,
                        )
                    }
                    Switch(checked = reminders, onCheckedChange = { reminders = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val sid = subjectId
                    if (sid == null) {
                        error = "Izaberi predmet"
                        return@Button
                    }
                    onSave(
                        EventEntity(
                            id = existing?.id ?: 0,
                            type = type,
                            subjectId = sid,
                            date = dateEpoch,
                            periodIndex = period,
                            note = note.trim(),
                            reminders = reminders,
                        ),
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("saveEvent"),
            ) { Text("Sačuvaj") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        dateEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        period = null
                        suggestPeriod(subjectId)
                    }
                    showDate = false
                }) { Text("U redu") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Otkaži") } },
        ) { DatePicker(state = dpState) }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Obrisati?",
            text = "${EventType.fromName(existing.type).label} će biti obrisan zajedno sa podsetnicima.",
            confirm = "Obriši",
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete(existing.id); onBack() },
        )
    }
}

private fun periodLabel(p: Int?, blocks: List<com.kostaking.raspored.core.LessonBlock>): String {
    if (p == null) return "Ceo dan / bez časa"
    val b = blocks.firstOrNull { p in it.periodIndices }
    return if (b != null) "$p. čas · ${b.subjectName}" else "$p. čas"
}
