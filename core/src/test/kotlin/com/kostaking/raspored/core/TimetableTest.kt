package com.kostaking.raspored.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TimetableTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val tt = Timetable(DefaultSchedule.scheduleData())

    @Test
    fun seedMatchesTheOriginalTimetable() {
        fun day(d: DayOfWeek) = tt.blocksFor(d).joinToString { "${it.numberLabel} ${it.subjectName} ${it.room}" }
        assertEquals(
            "0 Engleski 004, 1 Istorija 201, 2 Srpski 203, 3–4 Hemija 002, 5 Geografija 202, 6 Analiza 011, 7 ČOS 012",
            day(DayOfWeek.MONDAY),
        )
        assertEquals("2 Informatika 309, 3–4 Analiza 303, 5 Geografija 202, 6 Hemija 012", day(DayOfWeek.TUESDAY))
        assertEquals(
            "0–1 Informatika 308, 2 Engleski 109, 3 Istorija 201, 4 Fizičko 108, 5–6 Geometrija 301",
            day(DayOfWeek.WEDNESDAY),
        )
        assertEquals("1–2 Geometrija 301, 3–4 Fizika 302, 5–6 Srpski 301, 7 Veronauka 207", day(DayOfWeek.THURSDAY))
        assertEquals(
            "0 Srpski 200, 1 Fizičko 108, 2–3 IT Vežbe 310, 4 Analiza 303, 5–6 Fizika 012",
            day(DayOfWeek.FRIDAY),
        )
        assertTrue(tt.blocksFor(DayOfWeek.SATURDAY).isEmpty())
    }

    @Test
    fun periodTimesAreKeptVerbatim() {
        val p = DefaultSchedule.periods.joinToString { "${it.index}:${Format.time(it.start)}-${Format.time(it.end)}" }
        assertEquals(
            "0:07:35-08:20, 1:08:25-09:15, 2:09:15-10:00, 3:10:15-11:00, 4:11:05-11:50, 5:12:10-12:55, 6:13:00-13:45, 7:13:55-14:40",
            p,
        )
    }

    @Test
    fun blockSpansBothPeriods() {
        val hemija = tt.blocksFor(DayOfWeek.MONDAY).first { it.subjectName == "Hemija" }
        assertTrue(hemija.isBlock)
        assertEquals(LocalTime.of(10, 15), hemija.start)
        assertEquals(LocalTime.of(11, 50), hemija.end)
        assertEquals("3–4. čas", hemija.periodLabel)
        assertEquals(95, hemija.durationMinutes)
    }

    @Test
    fun lessonReferencingMissingPeriodIsTrimmed() {
        val data = DefaultSchedule.scheduleData()
        val periods = data.periods.filter { it.index <= 6 }
        val t = Timetable(data.copy(periods = periods))
        val wed = t.blocksFor(DayOfWeek.WEDNESDAY)
        assertEquals(5, wed.size)
        val thu = t.blocksFor(DayOfWeek.THURSDAY)
        assertTrue(thu.none { it.subjectName == "Veronauka" })
    }

    @Test
    fun schoolDaysAndDaysOff() {
        val off = DayOffInfo(1, monday.plusDays(1), monday.plusDays(1), "Praznik")
        val t = Timetable(DefaultSchedule.scheduleData().copy(daysOff = listOf(off)))
        assertTrue(t.isSchoolDay(monday))
        assertFalse(t.isSchoolDay(monday.plusDays(1)))
        assertEquals("Praznik", t.dayOffFor(monday.plusDays(1))?.label)
        assertFalse(t.isSchoolDay(monday.plusDays(5)))
        assertEquals(monday.plusDays(2), t.nextSchoolDay(monday))
        assertEquals(monday.plusWeeks(1), t.nextSchoolDay(monday.plusDays(4)))
        assertTrue(t.schoolBlocksFor(monday.plusDays(1)).isEmpty())
        assertTrue(t.blocksFor(monday.plusDays(1)).isNotEmpty())
    }

    @Test
    fun currentBlock() {
        assertEquals("Hemija", tt.currentBlock(monday, LocalTime.of(11, 2))?.subjectName)
        assertEquals("Hemija", tt.currentBlock(monday, LocalTime.of(10, 15))?.subjectName)
        assertNull(tt.currentBlock(monday, LocalTime.of(11, 50)))
        assertEquals("Srpski", tt.currentBlock(monday, LocalTime.of(9, 15))?.subjectName)
    }

    @Test
    fun timelineHasBreaksAndFreePeriods() {
        val items = DayTimeline.build(tt, tt.blocksFor(DayOfWeek.MONDAY))
        val breaks = items.filterIsInstance<TimelineItem.Break>().map { it.minutes }
        assertEquals(listOf(5L, 0L, 15L, 20L, 5L, 10L), breaks)
        assertEquals(7, items.count { it is TimelineItem.Lesson })

        val data = DefaultSchedule.scheduleData()
        val t = Timetable(data.copy(lessons = data.lessons.filterNot { it.day == DayOfWeek.MONDAY && it.startPeriod in 5..6 }))
        val withFree = DayTimeline.build(t, t.blocksFor(DayOfWeek.MONDAY))
        val free = withFree.filterIsInstance<TimelineItem.Free>().single()
        assertEquals("5–6. čas · slobodno", free.label)
        val idx = withFree.indexOf(free)
        assertEquals(20L, withFree[idx - 1].minutes)
        assertEquals(10L, withFree[idx + 1].minutes)
        // Tuesday starts with 2. čas - leading free periods are not shown
        assertTrue(DayTimeline.build(tt, tt.blocksFor(DayOfWeek.TUESDAY)).first() is TimelineItem.Lesson)
    }

    @Test
    fun progress() {
        val item = DayTimeline.build(tt, tt.blocksFor(DayOfWeek.MONDAY)).first()
        assertEquals(0f, DayTimeline.progress(item, LocalTime.of(7, 35))!!, 0.001f)
        assertEquals(0.5f, DayTimeline.progress(item, LocalTime.of(7, 57, 30))!!, 0.001f)
        assertNull(DayTimeline.progress(item, LocalTime.of(8, 20)))
        assertNull(DayTimeline.progress(item, LocalTime.of(7, 0)))
    }

    @Test
    fun pagerMapsWeekdaysAndSkipsWeekends() {
        val p = SchoolDayPager.pageFor(monday)
        assertEquals(monday, SchoolDayPager.dateFor(p))
        assertEquals(monday.plusDays(4), SchoolDayPager.dateFor(p + 4))
        assertEquals(monday.plusWeeks(1), SchoolDayPager.dateFor(p + 5))
        assertEquals(monday.minusDays(3), SchoolDayPager.dateFor(p - 1)) // previous Friday
        assertEquals(SchoolDayPager.pageFor(monday.plusWeeks(1)), SchoolDayPager.pageFor(monday.plusDays(5)))
        assertEquals(SchoolDayPager.pageFor(monday.plusWeeks(1)), SchoolDayPager.pageFor(monday.plusDays(6)))
        // round trip over a long range
        var d = LocalDate.of(2024, 1, 1)
        while (d.year < 2030) {
            if (!SchoolDayPager.isWeekend(d)) assertEquals(d, SchoolDayPager.dateFor(SchoolDayPager.pageFor(d)))
            d = d.plusDays(1)
        }
    }

    @Test
    fun formatting() {
        assertEquals("Četvrtak, 15. okt", Format.dayTitle(LocalDate.of(2026, 10, 15)))
        assertEquals("danas", Format.relativeDay(monday, monday))
        assertEquals("sutra", Format.relativeDay(monday, monday.plusDays(1)))
        assertEquals("za 3 dana", Format.relativeDay(monday, monday.plusDays(3)))
        assertEquals("za 21 dan", Format.relativeDay(monday, monday.plusDays(21)))
        assertEquals("za 11 dana", Format.relativeDay(monday, monday.plusDays(11)))
        assertEquals("juče", Format.relativeDay(monday, monday.minusDays(1)))
        assertEquals("Oktobar 2026", Format.monthTitle(2026, 10))
        assertEquals("1 h 35 min", Format.duration(95))
    }
}
