package com.kostaking.raspored.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** One numbered period ("čas") of the school day, e.g. 0. čas 07:35–08:20. */
data class PeriodSlot(
    val index: Int,
    val start: LocalTime,
    val end: LocalTime,
) {
    val label: String get() = "$index. čas"
}

/**
 * A lesson in the weekly timetable. A lesson can span several consecutive periods
 * ([span] > 1), which is how a "blok" (dvočas) is represented.
 */
data class LessonInfo(
    val id: Long,
    val day: DayOfWeek,
    val startPeriod: Int,
    val span: Int,
    val subjectId: Long,
    val subjectName: String,
    val room: String,
) {
    val endPeriod: Int get() = startPeriod + span - 1
}

enum class EventType(val label: String, val badge: String) {
    KONTROLNI("Kontrolni", "KONTROLNI"),
    PISMENI("Pismeni", "PISMENI"),
    TEST("Test", "TEST"),
    USMENO("Usmeno", "USMENO"),
    DOMACI("Domaći", "DOMAĆI"),
    OSTALO("Ostalo", "OBAVEZA");

    companion object {
        fun fromName(name: String?): EventType = entries.firstOrNull { it.name == name } ?: OSTALO
    }
}

/** A test / exam / homework entry ("kontrolni") on a concrete date. */
data class EventInfo(
    val id: Long,
    val type: EventType,
    val subjectId: Long,
    val subjectName: String,
    val date: LocalDate,
    /** Concrete period of that day, or null for "whole day / not tied to a period". */
    val periodIndex: Int?,
    val note: String = "",
    val remindersEnabled: Boolean = true,
)

/** A non-working day or a range of them (holiday, school break). Both ends inclusive. */
data class DayOffInfo(
    val id: Long,
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

data class ScheduleData(
    val periods: List<PeriodSlot>,
    val lessons: List<LessonInfo>,
    val events: List<EventInfo> = emptyList(),
    val daysOff: List<DayOffInfo> = emptyList(),
)

/** Lesson resolved against period times. */
data class LessonBlock(
    val lesson: LessonInfo,
    val periods: List<PeriodSlot>,
) {
    val start: LocalTime get() = periods.first().start
    val end: LocalTime get() = periods.last().end
    val firstPeriod: Int get() = periods.first().index
    val lastPeriod: Int get() = periods.last().index
    val isBlock: Boolean get() = periods.size > 1
    val periodIndices: IntRange get() = firstPeriod..lastPeriod
    val subjectName: String get() = lesson.subjectName
    val room: String get() = lesson.room

    /** "3. čas" or "3–4. čas" */
    val periodLabel: String
        get() = if (isBlock) "$firstPeriod–$lastPeriod. čas" else "$firstPeriod. čas"

    /** "3" or "3–4" */
    val numberLabel: String
        get() = if (isBlock) "$firstPeriod–$lastPeriod" else "$firstPeriod"

    val durationMinutes: Long
        get() = java.time.Duration.between(start, end).toMinutes()
}
