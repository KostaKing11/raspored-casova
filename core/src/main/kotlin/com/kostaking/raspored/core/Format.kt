package com.kostaking.raspored.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Serbian (Latin script) formatting helpers. */
object Format {

    fun time(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    fun minutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Ponedeljak"
        DayOfWeek.TUESDAY -> "Utorak"
        DayOfWeek.WEDNESDAY -> "Sreda"
        DayOfWeek.THURSDAY -> "Četvrtak"
        DayOfWeek.FRIDAY -> "Petak"
        DayOfWeek.SATURDAY -> "Subota"
        DayOfWeek.SUNDAY -> "Nedelja"
    }

    fun dayShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Pon"
        DayOfWeek.TUESDAY -> "Uto"
        DayOfWeek.WEDNESDAY -> "Sre"
        DayOfWeek.THURSDAY -> "Čet"
        DayOfWeek.FRIDAY -> "Pet"
        DayOfWeek.SATURDAY -> "Sub"
        DayOfWeek.SUNDAY -> "Ned"
    }

    private val monthShort = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")
    private val monthFull = listOf(
        "Januar", "Februar", "Mart", "April", "Maj", "Jun",
        "Jul", "Avgust", "Septembar", "Oktobar", "Novembar", "Decembar",
    )

    fun monthShort(month: Int): String = monthShort[month - 1]
    fun monthFull(month: Int): String = monthFull[month - 1]

    /** "Četvrtak, 15. okt" */
    fun dayTitle(date: LocalDate): String = "${dayName(date.dayOfWeek)}, ${date.dayOfMonth}. ${monthShort(date.monthValue)}"

    /** "čet, 15. okt" */
    fun shortDate(date: LocalDate): String =
        "${dayShort(date.dayOfWeek)}, ${date.dayOfMonth}. ${monthShort(date.monthValue)}"

    /** "15. okt 2026" */
    fun fullDate(date: LocalDate): String = "${date.dayOfMonth}. ${monthShort(date.monthValue)} ${date.year}."

    /** "Oktobar 2026" */
    fun monthTitle(year: Int, month: Int): String = "${monthFull(month)} $year"

    /** Serbian plural for "dan": 1 dan, 2 dana, 5 dana, 21 dan, 11 dana. */
    fun days(n: Long): String = if (n % 10 == 1L && n % 100 != 11L) "$n dan" else "$n dana"

    /** "danas", "sutra", "prekosutra", "za 5 dana", "juče", "pre 3 dana" */
    fun relativeDay(today: LocalDate, date: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, date)
        return when {
            diff == 0L -> "danas"
            diff == 1L -> "sutra"
            diff == 2L -> "prekosutra"
            diff > 2L -> "za ${days(diff)}"
            diff == -1L -> "juče"
            else -> "pre ${days(-diff)}"
        }
    }

    /** Label used in the day header: Danas / Sutra / Juče or null. */
    fun relativeHeader(today: LocalDate, date: LocalDate): String? =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Danas"
            1L -> "Sutra"
            -1L -> "Juče"
            else -> null
        }

    fun duration(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
