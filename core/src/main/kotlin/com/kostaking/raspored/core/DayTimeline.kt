package com.kostaking.raspored.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Items rendered top-to-bottom in the day view. */
sealed interface TimelineItem {
    val start: LocalTime
    val end: LocalTime
    val minutes: Long get() = Duration.between(start, end).toMinutes()

    data class Lesson(val block: LessonBlock, val index: Int) : TimelineItem {
        override val start: LocalTime get() = block.start
        override val end: LocalTime get() = block.end
    }

    /** Break between two consecutive items ("odmor 15 min"); may be 0 minutes long. */
    data class Break(override val start: LocalTime, override val end: LocalTime) : TimelineItem

    /** One or more empty periods between lessons. */
    data class Free(val periods: List<PeriodSlot>) : TimelineItem {
        override val start: LocalTime get() = periods.first().start
        override val end: LocalTime get() = periods.last().end
        val label: String
            get() = if (periods.size == 1) "${periods.first().index}. čas · slobodno"
            else "${periods.first().index}–${periods.last().index}. čas · slobodno"
    }
}

object DayTimeline {

    /**
     * Builds the visual timeline for one day: lessons, free periods in between and the
     * breaks separating them. Leading and trailing free periods are not shown.
     */
    fun build(tt: Timetable, blocks: List<LessonBlock>): List<TimelineItem> {
        if (blocks.isEmpty()) return emptyList()
        val items = mutableListOf<TimelineItem>()
        blocks.forEachIndexed { i, block ->
            if (i > 0) {
                val prev = blocks[i - 1]
                val freeSlots = ((prev.lastPeriod + 1) until block.firstPeriod)
                    .mapNotNull { tt.period(it) }
                    .filter { !it.start.isBefore(prev.end) && !it.end.isAfter(block.start) }
                if (freeSlots.isEmpty()) {
                    if (!block.start.isBefore(prev.end)) items += TimelineItem.Break(prev.end, block.start)
                } else {
                    items += TimelineItem.Break(prev.end, freeSlots.first().start)
                    items += TimelineItem.Free(freeSlots)
                    items += TimelineItem.Break(freeSlots.last().end, block.start)
                }
            }
            items += TimelineItem.Lesson(block, i)
        }
        return items
    }

    /** Progress 0..1 of [now] within [item] or null when outside. */
    fun progress(item: TimelineItem, now: LocalTime): Float? {
        if (now.isBefore(item.start) || !now.isBefore(item.end)) return null
        val total = ChronoUnit.SECONDS.between(item.start, item.end).toFloat()
        if (total <= 0f) return null
        return ChronoUnit.SECONDS.between(item.start, now) / total
    }
}

/**
 * Maps pager pages to school days (Monday–Friday). Page [ANCHOR_PAGE] is Monday [ANCHOR].
 */
object SchoolDayPager {
    val ANCHOR: LocalDate = LocalDate.of(2024, 1, 1) // a Monday
    const val PAGE_COUNT = 5 * 52 * 40
    const val ANCHOR_PAGE = 0

    fun dateFor(page: Int): LocalDate {
        val week = Math.floorDiv(page - ANCHOR_PAGE, 5)
        val day = Math.floorMod(page - ANCHOR_PAGE, 5)
        return ANCHOR.plusWeeks(week.toLong()).plusDays(day.toLong())
    }

    /** Page for [date]; weekend dates map to the following Monday. */
    fun pageFor(date: LocalDate): Int {
        val d = schoolDayOnOrAfter(date)
        val days = ChronoUnit.DAYS.between(ANCHOR, d)
        val week = Math.floorDiv(days, 7L)
        val dow = Math.floorMod(days, 7L)
        return (ANCHOR_PAGE + week * 5 + dow).toInt().coerceIn(0, PAGE_COUNT - 1)
    }

    /** Weekend → next Monday, weekday → itself. */
    fun schoolDayOnOrAfter(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> date.plusDays(2)
        java.time.DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
}
