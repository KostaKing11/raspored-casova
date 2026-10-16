@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.kostaking.raspored.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kostaking.raspored.alarm.PermissionSnapshot
import com.kostaking.raspored.core.DayOffInfo
import com.kostaking.raspored.core.DayTimeline
import com.kostaking.raspored.core.EventInfo
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.core.LessonBlock
import com.kostaking.raspored.core.SchoolDayPager
import com.kostaking.raspored.core.TimelineItem
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.ui.components.EventBadge
import com.kostaking.raspored.ui.components.Pill
import com.kostaking.raspored.ui.components.SubjectDot
import com.kostaking.raspored.ui.components.animateEnter
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.NowRed
import com.kostaking.raspored.ui.theme.WarnAmber
import com.kostaking.raspored.ui.theme.contentOn
import com.kostaking.raspored.ui.theme.subjectContainer
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.absoluteValue

private const val DP_PER_MINUTE = 2.0f

@Composable
fun DayScreen(
    state: ScheduleState,
    pagerState: PagerState,
    now: LocalDateTime,
    permissions: PermissionSnapshot?,
    onFixPermissions: () -> Unit,
    onAddEvent: (date: LocalDate, period: Int?, subjectId: Long?) -> Unit,
    onEditEvent: (Long) -> Unit,
    onEditLesson: (lessonId: Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = now.toLocalDate()
    val todayPage = SchoolDayPager.pageFor(today)
    val currentDate = SchoolDayPager.dateFor(pagerState.currentPage)
    val isWeekendToday = SchoolDayPager.isWeekend(today)

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentDate,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { it / 3 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 * dir } + fadeOut())
                    },
                    label = "title",
                ) { date ->
                    Column {
                        Text(
                            Format.dayTitle(date),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("dayTitle"),
                        )
                        val rel = Format.relativeHeader(today, date)
                        val sub = when {
                            isWeekendToday && date == SchoolDayPager.schoolDayOnOrAfter(today) -> "Vikend, sledeći je ponedeljak"
                            rel != null -> rel
                            else -> Format.relativeDay(today, date).replaceFirstChar { it.uppercase() }
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (rel == "Danas") MaterialTheme.colorScheme.primary else LocalExtraColors.current.textMuted,
                            modifier = Modifier.testTag("daySubtitle"),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = pagerState.currentPage != todayPage,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FilledTonalButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(todayPage) } },
                    modifier = Modifier.testTag("todayButton"),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(Icons.Rounded.Today, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isWeekendToday) "Ponedeljak" else "Danas")
                }
            }
            IconButton(onClick = { onAddEvent(currentDate, null, null) }, modifier = Modifier.testTag("addEventTop")) {
                Icon(Icons.Rounded.Add, contentDescription = "Dodaj kontrolni")
            }
        }

        if (permissions != null && !permissions.allGood) {
            PermissionWarning(permissions, onFixPermissions)
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().testTag("dayPager"),
            key = { it },
        ) { page ->
            val date = SchoolDayPager.dateFor(page)
            Box(
                Modifier.graphicsLayer {
                    val off = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    alpha = lerp(1f, 0.35f, off)
                    val s = lerp(1f, 0.9f, off)
                    scaleX = s
                    scaleY = s
                },
            ) {
                DayPage(
                    state = state,
                    date = date,
                    now = now,
                    onAddEvent = onAddEvent,
                    onEditEvent = onEditEvent,
                    onEditLesson = onEditLesson,
                )
            }
        }
    }
}

@Composable
private fun PermissionWarning(p: PermissionSnapshot, onFix: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(WarnAmber.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .border(1.dp, WarnAmber.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("permissionWarning"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = WarnAmber)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Notifikacije možda neće stići na vreme", style = MaterialTheme.typography.titleSmall)
            Text("Fali: " + p.missing.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted)
        }
        TextButton(onClick = onFix) { Text("Popravi") }
    }
}

@Composable
private fun DayPage(
    state: ScheduleState,
    date: LocalDate,
    now: LocalDateTime,
    onAddEvent: (LocalDate, Int?, Long?) -> Unit,
    onEditEvent: (Long) -> Unit,
    onEditLesson: (Long) -> Unit,
) {
    val tt = state.timetable
    val blocks = remember(state, date) { tt.blocksFor(date) }
    val items = remember(state, date) { DayTimeline.build(tt, blocks) }
    val dayOff = tt.dayOffFor(date)
    val isToday = date == now.toLocalDate()
    val nowTime: LocalTime? = if (isToday && dayOff == null) now.toLocalTime() else null
    val dayEvents = remember(state, date) { tt.eventsOn(date) }
    val unattached = remember(state, date) {
        dayEvents.filter { e -> blocks.none { b -> tt.eventsForBlock(date, b).any { it.id == e.id } } }
    }
    val nextBlock = nowTime?.let { t -> blocks.firstOrNull { it.start.isAfter(t) } }
    var sheetBlock by remember { mutableStateOf<LessonBlock?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().testTag("day-$date"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        if (dayOff != null) item(key = "off") { DayOffBanner(dayOff) }
        if (unattached.isNotEmpty()) {
            item(key = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    items(unattached, key = { it.id }) { e ->
                        EventChip(e, Color(state.color(e.subjectId))) { onEditEvent(e.id) }
                    }
                }
            }
        }
        if (nowTime != null && blocks.isNotEmpty()) {
            item(key = "status") { StatusLine(blocks, nowTime) }
        }
        if (blocks.isEmpty()) {
            item(key = "empty") { EmptyDay(date) }
        }
        itemsIndexed(items, key = { _, it -> "${it::class.simpleName}-${it.start}" }) { index, item ->
            Box(Modifier.animateEnter(index)) {
                when (item) {
                    is TimelineItem.Lesson -> {
                        val b = item.block
                        val color = Color(state.color(b.lesson.subjectId))
                        LessonCard(
                            block = b,
                            color = color,
                            events = tt.eventsForBlock(date, b),
                            nowTime = nowTime,
                            isNext = b == nextBlock,
                            dimmed = dayOff != null,
                            onClick = { sheetBlock = b },
                            onAddEvent = { onAddEvent(date, b.firstPeriod, b.lesson.subjectId) },
                            onEditLesson = { onEditLesson(b.lesson.id) },
                            onEditEvent = onEditEvent,
                        )
                    }
                    is TimelineItem.Break -> BreakRow(item, nowTime)
                    is TimelineItem.Free -> FreeCard(item, nowTime)
                }
            }
        }
        if (blocks.isNotEmpty()) {
            item(key = "footer") {
                Text(
                    "Kraj nastave u ${Format.time(blocks.last().end)} · ${blocks.sumOf { it.periods.size }} časova",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.textMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    sheetBlock?.let { b ->
        LessonSheet(
            block = b,
            date = date,
            color = Color(state.color(b.lesson.subjectId)),
            events = tt.eventsForBlock(date, b),
            onDismiss = { sheetBlock = null },
            onAddEvent = { sheetBlock = null; onAddEvent(date, b.firstPeriod, b.lesson.subjectId) },
            onEditLesson = { sheetBlock = null; onEditLesson(b.lesson.id) },
            onEditEvent = { sheetBlock = null; onEditEvent(it) },
        )
    }
}

@Composable
private fun StatusLine(blocks: List<LessonBlock>, now: LocalTime) {
    val first = blocks.first()
    val last = blocks.last()
    val current = blocks.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    val next = blocks.firstOrNull { it.start.isAfter(now) }
    val text = when {
        now.isBefore(first.start) -> "Nastava počinje za ${Format.duration(minutesUntil(now, first.start))}"
        !now.isBefore(last.end) -> "Nastava je završena za danas 🎉"
        current != null -> "Traje ${current.subjectName} · još ${Format.duration(minutesUntil(now, current.end))}"
        next != null -> "Odmor · ${next.subjectName} za ${Format.duration(minutesUntil(now, next.start))}"
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)) {
        Box(Modifier.size(8.dp).background(NowRed, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.testTag("statusLine"))
    }
}

private fun minutesUntil(from: LocalTime, to: LocalTime): Long =
    (Duration.between(from, to).seconds + 59) / 60

@Composable
private fun DayOffBanner(off: DayOffInfo) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.BeachAccess, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Neradni dan" + if (off.label.isNotBlank()) ": ${off.label}" else "", style = MaterialTheme.typography.titleSmall)
            Text("Nema nastave ni notifikacija. Raspored ispod je samo informativan.", style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted)
        }
    }
}

@Composable
private fun EmptyDay(date: LocalDate) {
    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.EventBusy, null, Modifier.size(48.dp), tint = LocalExtraColors.current.textMuted)
        Spacer(Modifier.height(8.dp))
        Text("Nema časova", style = MaterialTheme.typography.titleMedium)
        Text("${Format.dayName(date.dayOfWeek)} je prazan u rasporedu.", color = LocalExtraColors.current.textMuted)
    }
}

@Composable
private fun EventChip(e: EventInfo, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectDot(color, 8)
        Spacer(Modifier.width(6.dp))
        Text("${e.type.label} · ${e.subjectName}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LessonCard(
    block: LessonBlock,
    color: Color,
    events: List<EventInfo>,
    nowTime: LocalTime?,
    isNext: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onAddEvent: () -> Unit,
    onEditLesson: () -> Unit,
    onEditEvent: (Long) -> Unit,
) {
    val extra = LocalExtraColors.current
    val progress = nowTime?.let { t ->
        if (!t.isBefore(block.start) && t.isBefore(block.end)) {
            Duration.between(block.start, t).seconds.toFloat() / Duration.between(block.start, block.end).seconds
        } else null
    }
    val isCurrent = progress != null
    val isPast = nowTime != null && !nowTime.isBefore(block.end)
    val height = (block.durationMinutes * DP_PER_MINUTE).coerceAtLeast(86f).dp
    val shape = RoundedCornerShape(22.dp)
    var menu by remember { mutableStateOf(false) }
    val strength = if (isCurrent) 1.7f else 1f
    val textColor = extra.textStrong

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = if (isPast || dimmed) 0.5f else 1f }
            .background(
                Brush.horizontalGradient(listOf(subjectContainer(color, extra, strength), subjectContainer(color, extra, strength * 0.45f))),
                shape,
            )
            .border(if (isCurrent) 2.dp else 1.dp, color.copy(alpha = if (isCurrent) 0.95f else 0.35f), shape)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .drawWithContent {
                drawContent()
                // Internal break of a block (e.g. 5 min between 3. and 4. čas)
                if (block.isBlock) {
                    val total = Duration.between(block.start, block.end).seconds.toFloat()
                    for (k in 0 until block.periods.size - 1) {
                        val mid = Duration.between(block.start, block.periods[k].end).seconds / total
                        val y = size.height * mid
                        drawLine(
                            color.copy(alpha = 0.35f), Offset(72.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        )
                    }
                }
                if (progress != null) {
                    val y = size.height * progress
                    drawLine(NowRed, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(NowRed, radius = 5.dp.toPx(), center = Offset(6.dp.toPx(), y))
                }
            }
            .semantics { contentDescription = "Čas ${block.numberLabel} ${block.subjectName}" }
            .testTag("lesson-${block.firstPeriod}"),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(44.dp).background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        block.numberLabel,
                        color = contentOn(color),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (block.isBlock) 13.sp else 17.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        block.subjectName,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    if (block.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MeetingRoom, null, Modifier.size(15.dp), tint = color)
                            Spacer(Modifier.width(4.dp))
                            Text("učionica ${block.room}", style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.9f))
                        }
                    }
                    Text(
                        "${Format.time(block.start)} – ${Format.time(block.end)}" + if (block.isBlock) " · blok" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (events.isNotEmpty()) EventBadge(events.first().type, Modifier.testTag("badge-${block.firstPeriod}"), events.size - 1)
                    when {
                        isCurrent -> Pill("SADA", NowRed, filled = true)
                        isNext -> Pill("SLEDEĆI", color)
                    }
                }
            }
            if (progress != null) {
                val left = minutesUntil(nowTime, block.end)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("još $left min", style = MaterialTheme.typography.labelMedium, color = textColor)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Dodaj kontrolni za ovaj čas") },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { menu = false; onAddEvent() },
                modifier = Modifier.testTag("menuAddEvent"),
            )
            DropdownMenuItem(
                text = { Text("Izmeni čas") },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; onEditLesson() },
            )
            events.forEach { e ->
                DropdownMenuItem(
                    text = { Text("Izmeni: ${e.type.label}") },
                    leadingIcon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = { menu = false; onEditEvent(e.id) },
                )
            }
        }
    }
}

@Composable
private fun BreakRow(item: TimelineItem.Break, nowTime: LocalTime?) {
    val extra = LocalExtraColors.current
    val minutes = item.minutes
    val inBreak = nowTime != null && !nowTime.isBefore(item.start) && nowTime.isBefore(item.end)
    val progress = if (inBreak) DayTimeline.progress(item, nowTime) else null
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (minutes == 0L) 18.dp else 30.dp)
            .drawWithContent {
                drawContent()
                if (progress != null) {
                    val y = size.height * progress
                    drawLine(NowRed, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(NowRed, radius = 5.dp.toPx(), center = Offset(6.dp.toPx(), y))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(extra.divider))
            Text(
                if (minutes == 0L) "bez odmora" else "odmor $minutes min",
                style = MaterialTheme.typography.labelSmall,
                color = if (inBreak) NowRed else extra.textMuted,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Box(Modifier.weight(1f).height(1.dp).background(extra.divider))
        }
    }
}

@Composable
private fun FreeCard(item: TimelineItem.Free, nowTime: LocalTime?) {
    val extra = LocalExtraColors.current
    val shape = RoundedCornerShape(22.dp)
    val progress = nowTime?.let { DayTimeline.progress(item, it) }
    Box(
        Modifier
            .fillMaxWidth()
            .height((item.minutes * DP_PER_MINUTE * 0.6f).coerceAtLeast(56f).dp)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    extra.textMuted.copy(alpha = 0.5f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                )
                if (progress != null) {
                    val y = size.height * progress
                    drawLine(NowRed, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                    drawCircle(NowRed, radius = 5.dp.toPx(), center = Offset(6.dp.toPx(), y))
                }
            }
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(item.label, style = MaterialTheme.typography.titleSmall, color = extra.textMuted)
            Text("${Format.time(item.start)} – ${Format.time(item.end)}", style = MaterialTheme.typography.bodySmall, color = extra.textMuted)
        }
    }
}

@Composable
private fun LessonSheet(
    block: LessonBlock,
    date: LocalDate,
    color: Color,
    events: List<EventInfo>,
    onDismiss: () -> Unit,
    onAddEvent: () -> Unit,
    onEditLesson: () -> Unit,
    onEditEvent: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(block.numberLabel, color = contentOn(color), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(block.subjectName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${Format.dayTitle(date)} · ${block.periodLabel}",
                        color = LocalExtraColors.current.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoRow("Učionica", block.room.ifBlank { "—" })
            InfoRow("Vreme", "${Format.time(block.start)} – ${Format.time(block.end)} (${Format.duration(block.durationMinutes)})")
            if (block.isBlock) InfoRow("Blok", "${block.periods.size} časa zaredom")
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                events.forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(NowRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onEditEvent(e.id) })
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EventBadge(e.type)
                        Spacer(Modifier.width(10.dp))
                        Text(e.note.ifBlank { "bez beleške" }, Modifier.weight(1f))
                        Icon(Icons.Rounded.Edit, null)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddEvent, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Kontrolni")
                }
                OutlinedButton(onClick = onEditLesson, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Izmeni čas")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = LocalExtraColors.current.textMuted, modifier = Modifier.width(96.dp))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
