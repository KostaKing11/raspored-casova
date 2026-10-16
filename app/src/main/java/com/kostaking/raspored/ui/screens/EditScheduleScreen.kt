@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kostaking.raspored.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.data.LessonEntity
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.ui.components.SubScreen
import com.kostaking.raspored.ui.components.SubjectDropdown
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.contentOn
import com.kostaking.raspored.ui.theme.subjectContainer
import java.time.DayOfWeek

/** Request to open the lesson dialog: an existing lesson or an empty slot. */
data class LessonEditRequest(val day: Int, val startPeriod: Int, val lessonId: Long?)

@Composable
fun EditScheduleScreen(
    state: ScheduleState,
    initialDay: Int,
    initialLessonId: Long?,
    onBack: () -> Unit,
    onOpenPeriods: () -> Unit,
    onOpenSubjects: () -> Unit,
    onSaveLesson: (LessonEntity, String?) -> Unit,
    onDeleteLesson: (Long) -> Unit,
    onMerge: (Long) -> Unit,
    onSplit: (Long) -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(initialDay.coerceIn(1, 5)) }
    var editing by remember {
        mutableStateOf(
            initialLessonId?.let { id ->
                state.lessons.firstOrNull { it.id == id }?.let { LessonEditRequest(it.day, it.startPeriod, it.id) }
            },
        )
    }

    SubScreen(
        title = "Uredi raspored",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenPeriods) { Icon(Icons.Rounded.Schedule, "Termini časova") }
            IconButton(onClick = onOpenSubjects) { Icon(Icons.Rounded.Palette, "Predmeti i boje") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = day - 1, containerColor = Color.Transparent) {
                (1..5).forEach { d ->
                    Tab(
                        selected = day == d,
                        onClick = { day = d },
                        text = { Text(Format.dayShort(DayOfWeek.of(d))) },
                        modifier = Modifier.testTag("tab-$d"),
                    )
                }
            }
            val lessons = state.lessons.filter { it.day == day }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        "Tapni čas da ga izmeniš, ili prazan termin da dodaš čas. Meni ⋮ spaja čas sa sledećim u blok ili razdvaja blok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalExtraColors.current.textMuted,
                    )
                }
                items(state.periods, key = { it.idx }) { p ->
                    val covering = lessons.firstOrNull { p.idx in it.startPeriod until it.startPeriod + it.span }
                    when {
                        covering == null -> EmptySlotRow(p.idx, Format.minutesOfDay(p.startMin)) {
                            editing = LessonEditRequest(day, p.idx, null)
                        }
                        covering.startPeriod == p.idx -> LessonRow(
                            state = state,
                            lesson = covering,
                            canMerge = state.periods.any { it.idx == covering.startPeriod + covering.span },
                            onClick = { editing = LessonEditRequest(day, covering.startPeriod, covering.id) },
                            onMerge = { onMerge(covering.id) },
                            onSplit = { onSplit(covering.id) },
                            onDelete = { onDeleteLesson(covering.id) },
                        )
                        else -> Unit // covered by a block that started earlier
                    }
                }
            }
        }
    }

    editing?.let { req ->
        LessonEditDialog(
            state = state,
            request = req,
            onDismiss = { editing = null },
            onSave = { lesson, newName -> onSaveLesson(lesson, newName); editing = null },
            onDelete = { id -> onDeleteLesson(id); editing = null },
        )
    }
}

@Composable
private fun EmptySlotRow(idx: Int, start: String, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, extra.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("slot-$idx"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$idx.", fontWeight = FontWeight.Bold, color = extra.textMuted, modifier = Modifier.width(36.dp))
        Text("slobodno · $start", color = extra.textMuted, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Add, "Dodaj čas", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LessonRow(
    state: ScheduleState,
    lesson: LessonEntity,
    canMerge: Boolean,
    onClick: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extra = LocalExtraColors.current
    val color = Color(state.color(lesson.subjectId))
    val first = state.periods.firstOrNull { it.idx == lesson.startPeriod }
    val last = state.periods.firstOrNull { it.idx == lesson.startPeriod + lesson.span - 1 } ?: first
    var menu by remember { mutableStateOf(false) }
    val label = if (lesson.span > 1) "${lesson.startPeriod}–${lesson.startPeriod + lesson.span - 1}" else "${lesson.startPeriod}"
    Row(
        Modifier
            .fillMaxWidth()
            .background(subjectContainer(color, extra), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
            .height(if (lesson.span > 1) 72.dp else 48.dp)
            .testTag("lessonRow-${lesson.startPeriod}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(label, color = contentOn(color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.subjectName(lesson.subjectId), style = MaterialTheme.typography.titleMedium)
            val time = if (first != null && last != null) "${Format.minutesOfDay(first.startMin)}–${Format.minutesOfDay(last.endMin)}" else ""
            Text(
                listOf("učionica ${lesson.room}".takeIf { lesson.room.isNotBlank() }, time, "blok".takeIf { lesson.span > 1 })
                    .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = extra.textMuted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("lessonMenu-${lesson.startPeriod}")) {
                Icon(Icons.Rounded.MoreVert, "Opcije")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Izmeni") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onClick() })
                if (canMerge) {
                    DropdownMenuItem(
                        text = { Text("Spoji sa sledećim (blok)") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CallMerge, null) },
                        onClick = { menu = false; onMerge() },
                        modifier = Modifier.testTag("merge"),
                    )
                }
                if (lesson.span > 1) {
                    DropdownMenuItem(
                        text = { Text("Razdvoji blok") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CallSplit, null) },
                        onClick = { menu = false; onSplit() },
                        modifier = Modifier.testTag("split"),
                    )
                }
                DropdownMenuItem(text = { Text("Obriši") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun LessonEditDialog(
    state: ScheduleState,
    request: LessonEditRequest,
    onDismiss: () -> Unit,
    onSave: (LessonEntity, String?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val existing = state.lessons.firstOrNull { it.id == request.lessonId }
    var subjectId by remember { mutableStateOf(existing?.subjectId) }
    var newName by remember { mutableStateOf<String?>(null) }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var span by remember { mutableStateOf(existing?.span ?: 1) }
    val start = existing?.startPeriod ?: request.startPeriod
    val maxSpan = generateSequence(start) { it + 1 }
        .takeWhile { idx -> state.periods.any { it.idx == idx } }
        .count()
        .coerceIn(1, 4)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Format.dayName(DayOfWeek.of(request.day))} · $start. čas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (newName == null) {
                    SubjectDropdown(
                        subjects = state.subjects,
                        selectedId = subjectId,
                        onSelect = { subjectId = it.id },
                        onNew = { newName = "" },
                    )
                } else {
                    OutlinedTextField(
                        value = newName.orEmpty(),
                        onValueChange = { newName = it },
                        label = { Text("Naziv novog predmeta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("newSubject"),
                        trailingIcon = { TextButton(onClick = { newName = null }) { Text("Lista") } },
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Učionica") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("room"),
                )
                Text("Trajanje", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..maxSpan).forEach { s ->
                        FilterChip(
                            selected = span == s,
                            onClick = { span = s },
                            label = { Text(if (s == 1) "1 čas" else "blok ($s)") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = newName?.trim()
                    val sid = subjectId
                    if (name.isNullOrEmpty() && sid == null) return@TextButton
                    onSave(
                        LessonEntity(
                            id = existing?.id ?: 0,
                            day = request.day,
                            startPeriod = start,
                            span = span,
                            subjectId = sid ?: 0,
                            room = room.trim(),
                        ),
                        name?.takeIf { it.isNotEmpty() },
                    )
                },
                modifier = Modifier.testTag("saveLesson"),
            ) { Text("Sačuvaj") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text("Obriši", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Otkaži") }
            }
        },
    )
}
