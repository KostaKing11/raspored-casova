package com.kostaking.raspored.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class NotificationKind(val label: String) {
    NEXT_LESSON("Sledeći čas"),
    BLOCK_PART("Između delova bloka"),
    END_OF_DAY("Kraj nastave"),
    BEFORE_FIRST("Pre prvog časa"),
    EVENT_EVENING("Kontrolni – veče pre"),
    EVENT_MORNING("Kontrolni – jutro"),
    TEST("Test"),
    SIMULATED("Simulacija");

    companion object {
        fun fromName(name: String?): NotificationKind = entries.firstOrNull { it.name == name } ?: TEST
    }
}

data class PlannedNotification(
    val at: LocalDateTime,
    val kind: NotificationKind,
    val title: String,
    val text: String,
    /** Stable identity of this notification (used for de-duplication). */
    val key: String,
    val hasEvent: Boolean = false,
)

data class PlannerSettings(
    val nextLessonEnabled: Boolean = true,
    val betweenBlockParts: Boolean = false,
    val endOfDayEnabled: Boolean = true,
    val beforeFirstEnabled: Boolean = false,
    val beforeFirstMinutes: Int = 10,
    val eventEveningEnabled: Boolean = true,
    val eventEveningTime: LocalTime = LocalTime.of(19, 0),
    val eventMorningEnabled: Boolean = true,
    val eventMorningTime: LocalTime = LocalTime.of(7, 0),
)

/**
 * Pure planning logic: given the timetable, settings and "now", computes every notification
 * that should fire in the next [horizon] days, each at its exact wall-clock time.
 */
object NotificationPlanner {

    const val DEFAULT_HORIZON_DAYS = 7

    fun plan(
        tt: Timetable,
        settings: PlannerSettings,
        now: LocalDateTime,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): List<PlannedNotification> {
        val today = now.toLocalDate()
        val end = today.plusDays(horizonDays.toLong()).atTime(LocalTime.MAX)
        val result = mutableListOf<PlannedNotification>()
        for (offset in 0..horizonDays) {
            result += lessonNotificationsForDay(tt, settings, today.plusDays(offset.toLong()))
        }
        result += eventReminders(tt, settings)
        return result
            .filter { it.at.isAfter(now) && !it.at.isAfter(end) }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.at }, { it.kind.ordinal }))
    }

    /** Lesson-related notifications of a single date (empty on weekends and non-working days). */
    fun lessonNotificationsForDay(
        tt: Timetable,
        settings: PlannerSettings,
        date: LocalDate,
    ): List<PlannedNotification> {
        val blocks = tt.schoolBlocksFor(date)
        if (blocks.isEmpty()) return emptyList()
        val out = mutableListOf<PlannedNotification>()

        if (settings.beforeFirstEnabled) {
            out += beforeFirst(tt, date, blocks.first(), settings.beforeFirstMinutes)
        }
        blocks.forEachIndexed { i, block ->
            if (settings.betweenBlockParts && block.isBlock) {
                for (k in 0 until block.periods.size - 1) {
                    out += blockPart(tt, date, block, k)
                }
            }
            val next = blocks.getOrNull(i + 1)
            if (next != null) {
                if (settings.nextLessonEnabled) out += nextLesson(tt, date, block, next)
            } else if (settings.endOfDayEnabled) {
                out += endOfDay(tt, date, block)
            }
        }
        return out
    }

    /**
     * What fires when [blockIndex] of [date]'s timetable ends, ignoring the on/off settings.
     * Used by the "simulate end of lesson" debug action.
     */
    fun afterBlock(tt: Timetable, date: LocalDate, blockIndex: Int): PlannedNotification? {
        val blocks = tt.blocksFor(date)
        val block = blocks.getOrNull(blockIndex) ?: return null
        val next = blocks.getOrNull(blockIndex + 1)
        return if (next != null) nextLesson(tt, date, block, next) else endOfDay(tt, date, block)
    }

    fun nextLesson(tt: Timetable, date: LocalDate, ended: LessonBlock, next: LessonBlock): PlannedNotification {
        val events = tt.eventsForBlock(date, next)
        val title = buildString {
            append("Sledeći čas: ").append(next.subjectName)
            if (events.isNotEmpty()) append(" · ⚠ ").append(events.first().type.badge)
        }
        val line = listOfNotNull(
            next.room.takeIf { it.isNotBlank() }?.let { "učionica $it" },
            "počinje u ${Format.time(next.start)}",
            next.periodLabel,
        ).joinToString(" · ")
        return PlannedNotification(
            at = date.atTime(ended.end),
            kind = NotificationKind.NEXT_LESSON,
            title = title,
            text = (listOf(line) + events.map(::eventLine)).joinToString("\n"),
            key = "next-$date-${ended.lastPeriod}",
            hasEvent = events.isNotEmpty(),
        )
    }

    fun blockPart(tt: Timetable, date: LocalDate, block: LessonBlock, partIndex: Int): PlannedNotification {
        val endedPart = block.periods[partIndex]
        val nextPart = block.periods[partIndex + 1]
        val events = tt.eventsForBlock(date, block)
        val line = listOfNotNull(
            block.room.takeIf { it.isNotBlank() }?.let { "učionica $it" },
            "${nextPart.index}. čas počinje u ${Format.time(nextPart.start)}",
        ).joinToString(" · ")
        return PlannedNotification(
            at = date.atTime(endedPart.end),
            kind = NotificationKind.BLOCK_PART,
            title = "Nastavlja se: ${block.subjectName}",
            text = (listOf(line) + events.map(::eventLine)).joinToString("\n"),
            key = "part-$date-${endedPart.index}",
            hasEvent = events.isNotEmpty(),
        )
    }

    fun endOfDay(tt: Timetable, date: LocalDate, last: LessonBlock): PlannedNotification {
        val nextDay = tt.nextSchoolDay(date)
        val lines = mutableListOf<String>()
        var hasEvent = false
        if (nextDay != null) {
            val prefix = whenWord(date, nextDay)
            val events = tt.eventsOn(nextDay)
            if (events.isNotEmpty()) {
                hasEvent = true
                events.forEach { e ->
                    val period = e.periodIndex?.let { " ($it. čas)" } ?: ""
                    lines += "$prefix: ⚠ ${e.type.badge} · ${e.subjectName}$period"
                }
            } else {
                tt.blocksFor(nextDay).firstOrNull()?.let { first ->
                    lines += "$prefix prvi čas u ${Format.time(first.start)} · ${first.subjectName}"
                }
            }
        }
        return PlannedNotification(
            at = date.atTime(last.end),
            kind = NotificationKind.END_OF_DAY,
            title = "Kraj nastave za danas 🎉",
            text = lines.joinToString("\n"),
            key = "eod-$date",
            hasEvent = hasEvent,
        )
    }

    fun beforeFirst(tt: Timetable, date: LocalDate, first: LessonBlock, minutes: Int): PlannedNotification {
        val events = tt.eventsForBlock(date, first)
        val line = listOfNotNull(
            first.room.takeIf { it.isNotBlank() }?.let { "učionica $it" },
            "${first.periodLabel} počinje u ${Format.time(first.start)}",
        ).joinToString(" · ")
        return PlannedNotification(
            at = date.atTime(first.start).minusMinutes(minutes.toLong().coerceAtLeast(0)),
            kind = NotificationKind.BEFORE_FIRST,
            title = "Za $minutes min: ${first.subjectName}",
            text = (listOf(line) + events.map(::eventLine)).joinToString("\n"),
            key = "first-$date",
            hasEvent = events.isNotEmpty(),
        )
    }

    fun eventReminders(tt: Timetable, settings: PlannerSettings): List<PlannedNotification> {
        val out = mutableListOf<PlannedNotification>()
        for (e in tt.data.events) {
            if (!e.remindersEnabled) continue
            val details = eventDetails(tt, e)
            if (settings.eventEveningEnabled) {
                out += PlannedNotification(
                    at = e.date.minusDays(1).atTime(settings.eventEveningTime),
                    kind = NotificationKind.EVENT_EVENING,
                    title = "Sutra: ${e.type.label} · ${e.subjectName}",
                    text = details,
                    key = "event-${e.id}-evening",
                    hasEvent = true,
                )
            }
            if (settings.eventMorningEnabled) {
                out += PlannedNotification(
                    at = e.date.atTime(settings.eventMorningTime),
                    kind = NotificationKind.EVENT_MORNING,
                    title = "Danas: ${e.type.label} · ${e.subjectName}",
                    text = details,
                    key = "event-${e.id}-morning",
                    hasEvent = true,
                )
            }
        }
        return out
    }

    private fun eventDetails(tt: Timetable, e: EventInfo): String {
        val period = e.periodIndex?.let { idx ->
            tt.period(idx)?.let { "$idx. čas u ${Format.time(it.start)}" } ?: "$idx. čas"
        }
        val parts = listOfNotNull(period, e.note.trim().takeIf { it.isNotEmpty() })
        return if (parts.isEmpty()) "Ne zaboravi da se spremiš 📚" else parts.joinToString(" · ")
    }

    private fun eventLine(e: EventInfo): String {
        val note = e.note.trim()
        return if (note.isEmpty()) "⚠ ${e.type.badge} na ovom času" else "⚠ ${e.type.badge}: $note"
    }

    /** "Sutra" when [next] is the day after [from], otherwise "U ponedeljak" etc. */
    fun whenWord(from: LocalDate, next: LocalDate): String =
        if (next == from.plusDays(1)) "Sutra" else "U " + accusativeDay(next.dayOfWeek)

    private fun accusativeDay(day: DayOfWeek): String = when (day) {
        DayOfWeek.WEDNESDAY -> "sredu"
        DayOfWeek.SUNDAY -> "nedelju"
        DayOfWeek.SATURDAY -> "subotu"
        else -> Format.dayName(day).lowercase()
    }
}
