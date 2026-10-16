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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.core.EventInfo
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.core.SchoolDayPager
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.ui.components.SubjectDot
import com.kostaking.raspored.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private const val MONTH_PAGES = 1200
private const val MONTH_ANCHOR = MONTH_PAGES / 2

@Composable
fun CalendarScreen(
    state: ScheduleState,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onAddEvent: (LocalDate) -> Unit,
    onEditEvent: (Long) -> Unit,
) {
    val baseMonth = remember(today) { YearMonth.from(today) }
    val pager = rememberPagerState(initialPage = MONTH_ANCHOR) { MONTH_PAGES }
    val scope = rememberCoroutineScope()
    val month = baseMonth.plusMonths((pager.currentPage - MONTH_ANCHOR).toLong())
    val eventsByDate = remember(state) { state.timetable.data.events.groupBy { it.date } }
    var weekendDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Format.monthTitle(month.year, month.monthValue),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).testTag("monthTitle"),
            )
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Prethodni mesec")
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sledeći mesec")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    Format.dayShort(d),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.value >= 6) LocalExtraColors.current.textMuted else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val ym = baseMonth.plusMonths((page - MONTH_ANCHOR).toLong())
            MonthGrid(
                state = state,
                month = ym,
                today = today,
                eventsByDate = eventsByDate,
                onDay = { d ->
                    if (SchoolDayPager.isWeekend(d)) weekendDay = d else onOpenDay(d)
                },
            )
        }
        val monthEvents = state.timetable.data.events
            .filter { YearMonth.from(it.date) == month }
            .sortedWith(compareBy({ it.date }, { it.periodIndex ?: 99 }))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    if (monthEvents.isEmpty()) "Nema obaveza ovog meseca" else "Obaveze ovog meseca",
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalExtraColors.current.textMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(monthEvents, key = { it.id }) { e ->
                EventRow(e, Color(state.color(e.subjectId)), today) { onEditEvent(e.id) }
            }
        }
    }

    weekendDay?.let { d ->
        val list = eventsByDate[d].orEmpty()
        AlertDialog(
            onDismissRequest = { weekendDay = null },
            title = { Text(Format.dayTitle(d)) },
            text = {
                Column {
                    Text("Vikend – nema nastave.")
                    list.forEach { e ->
                        Spacer(Modifier.height(8.dp))
                        Text("• ${e.type.label} · ${e.subjectName}", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { weekendDay = null; onAddEvent(d) }) { Text("Dodaj obavezu") } },
            dismissButton = { TextButton(onClick = { weekendDay = null }) { Text("Zatvori") } },
        )
    }
}

@Composable
private fun MonthGrid(
    state: ScheduleState,
    month: YearMonth,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<EventInfo>>,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7
    val tt = state.timetable
    Column(Modifier.padding(horizontal = 12.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp)) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isWeekend = c >= 5,
                                isOff = tt.dayOffFor(date) != null,
                                dots = eventsByDate[date].orEmpty().map { Color(state.color(it.subjectId)) },
                                onClick = { onDay(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isWeekend: Boolean,
    isOff: Boolean,
    dots: List<Color>,
    onClick: () -> Unit,
) {
    val extra = LocalExtraColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(
                when {
                    isOff -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    dots.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                shape,
            )
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 6.dp)
            .testTag("cal-$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isWeekend || isOff -> extra.textMuted
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.take(3).forEach { SubjectDot(it, 7) }
        }
        if (isOff) {
            Text("neradno", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
fun EventRow(e: EventInfo, color: Color, today: LocalDate, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (extra.isDark) Color(0xCC0E1224) else Color.White, shape)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("event-${e.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(44.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${e.type.label} · ${e.subjectName}", style = MaterialTheme.typography.titleMedium)
            val period = e.periodIndex?.let { " · $it. čas" } ?: ""
            Text(Format.shortDate(e.date) + period, style = MaterialTheme.typography.bodySmall, color = extra.textMuted)
            if (e.note.isNotBlank()) {
                Text(e.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
        val rel = Format.relativeDay(today, e.date)
        val soon = !e.date.isAfter(today.plusDays(2)) && !e.date.isBefore(today)
        Text(
            rel,
            style = MaterialTheme.typography.labelLarge,
            color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}
