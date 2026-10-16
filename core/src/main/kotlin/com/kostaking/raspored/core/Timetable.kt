package com.kostaking.raspored.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Read-only queries over [ScheduleData]. */
class Timetable(val data: ScheduleData) {

    private val periodsByIndex: Map<Int, PeriodSlot> = data.periods.associateBy { it.index }

    fun period(index: Int): PeriodSlot? = periodsByIndex[index]

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun dayOffFor(date: LocalDate): DayOffInfo? = data.daysOff.firstOrNull { date in it }

    /** A school day is a weekday that is not marked as a non-working day. */
    fun isSchoolDay(date: LocalDate): Boolean = !isWeekend(date) && dayOffFor(date) == null

    /**
     * Lessons of a weekday resolved to times, sorted by start time. Lessons that reference
     * periods which don't exist are trimmed (or dropped when nothing remains).
     */
    fun blocksFor(day: DayOfWeek): List<LessonBlock> =
        data.lessons
            .filter { it.day == day }
            .mapNotNull { lesson ->
                val slots = (lesson.startPeriod until lesson.startPeriod + lesson.span.coerceAtLeast(1))
                    .map { periodsByIndex[it] }
                    .takeWhile { it != null }
                    .filterNotNull()
                if (slots.isEmpty()) null else LessonBlock(lesson, slots)
            }
            .sortedWith(compareBy({ it.start }, { it.firstPeriod }))

    fun blocksFor(date: LocalDate): List<LessonBlock> = blocksFor(date.dayOfWeek)

    /** Blocks that actually take place on [date] (empty on weekends / non-working days). */
    fun schoolBlocksFor(date: LocalDate): List<LessonBlock> =
        if (isSchoolDay(date)) blocksFor(date) else emptyList()

    fun eventsOn(date: LocalDate): List<EventInfo> =
        data.events.filter { it.date == date }.sortedWith(compareBy({ it.periodIndex ?: Int.MAX_VALUE }, { it.id }))

    /**
     * Events that belong to [block] on [date]: either explicitly tied to one of its periods,
     * or not tied to a period but for the same subject.
     */
    fun eventsForBlock(date: LocalDate, block: LessonBlock): List<EventInfo> =
        eventsOn(date).filter { e ->
            if (e.periodIndex != null) e.periodIndex in block.periodIndices
            else e.subjectId == block.lesson.subjectId
        }

    /** Next school day strictly after [date], or null if none within [maxDays]. */
    fun nextSchoolDay(date: LocalDate, maxDays: Int = 400): LocalDate? {
        var d = date.plusDays(1)
        repeat(maxDays) {
            if (isSchoolDay(d) && blocksFor(d).isNotEmpty()) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** Block that is running at [time] on [date], if any. */
    fun currentBlock(date: LocalDate, time: LocalTime): LessonBlock? =
        blocksFor(date).firstOrNull { !time.isBefore(it.start) && time.isBefore(it.end) }
}
