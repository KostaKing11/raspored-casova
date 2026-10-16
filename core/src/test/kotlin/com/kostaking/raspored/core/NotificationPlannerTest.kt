package com.kostaking.raspored.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NotificationPlannerTest {

    // 2026-10-12 is a Monday.
    private val monday = LocalDate.of(2026, 10, 12)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)
    private val friday = monday.plusDays(4)
    private val saturday = monday.plusDays(5)
    private val sunday = monday.plusDays(6)

    private val base = DefaultSchedule.scheduleData()
    private val defaults = PlannerSettings()

    private fun tt(
        events: List<EventInfo> = emptyList(),
        daysOff: List<DayOffInfo> = emptyList(),
    ) = Timetable(base.copy(events = events, daysOff = daysOff))

    private fun subjectId(name: String) = base.lessons.first { it.subjectName == name }.subjectId

    private fun at(date: LocalDate, hhmm: String) = date.atTime(LocalTime.parse(hhmm))

    private fun lessonOnly(s: PlannerSettings = defaults) =
        s.copy(eventEveningEnabled = false, eventMorningEnabled = false)

    @Test
    fun mondayNotificationTimesAreExactLessonEnds() {
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, monday)
        val times = list.map { Format.time(it.at.toLocalTime()) }
        // 0,1,2 single; 3-4 block (one notification at 11:50); 5,6; 7 is the last -> end of day
        assertEquals(listOf("08:20", "09:15", "10:00", "11:50", "12:55", "13:45", "14:40"), times)
        assertEquals(NotificationKind.END_OF_DAY, list.last().kind)
        assertTrue(list.dropLast(1).all { it.kind == NotificationKind.NEXT_LESSON })
        assertTrue(list.all { it.at.second == 0 && it.at.nano == 0 })
    }

    @Test
    fun nextLessonTextNamesSubjectRoomAndStart() {
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, thursday)
        // Thursday: 1-2 Geometrija ends 10:00 -> next 3-4 Fizika 302 at 10:15
        val n = list.first { it.at == at(thursday, "10:00") }
        assertEquals("Sledeći čas: Fizika", n.title)
        assertEquals("učionica 302 · počinje u 10:15 · 3–4. čas", n.text)
        assertFalse(n.hasEvent)
    }

    @Test
    fun touchingPeriodsStillNotifyAtTheSharedMinute() {
        // Monday 1. čas ends 09:15 and 2. čas starts 09:15.
        val n = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, monday)
            .first { it.at == at(monday, "09:15") }
        assertEquals("Sledeći čas: Srpski", n.title)
        assertTrue(n.text.contains("počinje u 09:15"))
    }

    @Test
    fun blockNotifiesOnlyAfterWholeBlockByDefault() {
        val times = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, monday).map { it.at }
        assertFalse(at(monday, "11:00") in times) // between Hemija 3 and 4
        assertTrue(at(monday, "11:50") in times)
        val n = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, monday).first { it.at == at(monday, "11:50") }
        assertEquals("Sledeći čas: Geografija", n.title)
    }

    @Test
    fun blockPartNotificationWhenEnabled() {
        val s = defaults.copy(betweenBlockParts = true)
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), s, monday)
        val part = list.single { it.kind == NotificationKind.BLOCK_PART }
        assertEquals(at(monday, "11:00"), part.at)
        assertEquals("Nastavlja se: Hemija", part.title)
        assertEquals("učionica 002 · 4. čas počinje u 11:05", part.text)
        // the regular notification after the block is still there
        assertTrue(list.any { it.at == at(monday, "11:50") && it.kind == NotificationKind.NEXT_LESSON })
    }

    @Test
    fun lastLessonProducesEndOfDayWithNextDayInfo() {
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, thursday)
        val eod = list.last()
        assertEquals(NotificationKind.END_OF_DAY, eod.kind)
        assertEquals(at(thursday, "14:40"), eod.at) // Veronauka 7. čas
        assertEquals("Kraj nastave za danas 🎉", eod.title)
        assertEquals("Sutra prvi čas u 07:35 · Srpski", eod.text)
    }

    @Test
    fun endOfDayMentionsTomorrowsTest() {
        val e = EventInfo(1, EventType.KONTROLNI, subjectId("Fizika"), "Fizika", friday, 5, "kinematika")
        val eod = NotificationPlanner.lessonNotificationsForDay(tt(listOf(e)), defaults, thursday).last()
        assertTrue(eod.hasEvent)
        assertEquals("Sutra: ⚠ KONTROLNI · Fizika (5. čas)", eod.text)
    }

    @Test
    fun fridayEndOfDayTalksAboutMonday() {
        val e = EventInfo(1, EventType.TEST, subjectId("Engleski"), "Engleski", monday.plusWeeks(1), null)
        val eod = NotificationPlanner.lessonNotificationsForDay(tt(listOf(e)), defaults, friday).last()
        assertEquals(at(friday, "13:45"), eod.at) // Fizika block 5-6 ends 13:45, 7 is free
        assertEquals("U ponedeljak: ⚠ TEST · Engleski", eod.text)
    }

    @Test
    fun endOfDayCanBeDisabled() {
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), defaults.copy(endOfDayEnabled = false), thursday)
        assertTrue(list.none { it.kind == NotificationKind.END_OF_DAY })
        assertEquals(at(thursday, "13:45"), list.last().at)
    }

    @Test
    fun freePeriodsAreSkippedAndTrailingFreeMeansEarlierEnd() {
        // Tuesday: 0-1 free, 2 Informatika, 3-4 Analiza, 5 Geografija, 6 Hemija, 7 free
        val list = NotificationPlanner.lessonNotificationsForDay(tt(), defaults, tuesday)
        assertEquals(
            listOf("10:00", "11:50", "12:55", "13:45"),
            list.map { Format.time(it.at.toLocalTime()) },
        )
        assertEquals(NotificationKind.END_OF_DAY, list.last().kind)
    }

    @Test
    fun freePeriodInTheMiddleLeadsToTheLessonAfterIt() {
        val data = base.copy(lessons = base.lessons.filterNot { it.day == DayOfWeek.MONDAY && it.startPeriod == 5 })
        val list = NotificationPlanner.lessonNotificationsForDay(Timetable(data), defaults, monday)
        val n = list.first { it.at == at(monday, "11:50") }
        assertEquals("Sledeći čas: Analiza", n.title)
        assertTrue(n.text.contains("počinje u 13:00"))
        assertFalse(list.any { it.at == at(monday, "12:55") })
    }

    @Test
    fun beforeFirstLessonIsOptional() {
        assertTrue(NotificationPlanner.lessonNotificationsForDay(tt(), defaults, wednesday).none { it.kind == NotificationKind.BEFORE_FIRST })
        val s = defaults.copy(beforeFirstEnabled = true, beforeFirstMinutes = 15)
        val first = NotificationPlanner.lessonNotificationsForDay(tt(), s, tuesday).first()
        assertEquals(NotificationKind.BEFORE_FIRST, first.kind)
        assertEquals(at(tuesday, "09:00"), first.at) // Tuesday starts with 2. čas at 09:15
        assertEquals("Za 15 min: Informatika", first.title)
    }

    @Test
    fun noLessonNotificationsOnWeekend() {
        assertTrue(NotificationPlanner.lessonNotificationsForDay(tt(), defaults, saturday).isEmpty())
        assertTrue(NotificationPlanner.lessonNotificationsForDay(tt(), defaults, sunday).isEmpty())
    }

    @Test
    fun noLessonNotificationsOnDaysOff() {
        val off = DayOffInfo(1, tuesday, thursday, "Raspust")
        val t = tt(daysOff = listOf(off))
        assertTrue(NotificationPlanner.lessonNotificationsForDay(t, defaults, tuesday).isEmpty())
        assertTrue(NotificationPlanner.lessonNotificationsForDay(t, defaults, wednesday).isEmpty())
        assertTrue(NotificationPlanner.lessonNotificationsForDay(t, defaults, thursday).isEmpty())
        assertTrue(NotificationPlanner.lessonNotificationsForDay(t, defaults, friday).isNotEmpty())
        // Monday's end-of-day skips the holiday and points at Friday
        val eod = NotificationPlanner.lessonNotificationsForDay(t, defaults, monday).last()
        assertEquals("U petak prvi čas u 07:35 · Srpski", eod.text)
    }

    @Test
    fun testOnNextLessonIsInTheNotification() {
        val e = EventInfo(7, EventType.KONTROLNI, subjectId("Fizika"), "Fizika", thursday, 3, "Njutnovi zakoni")
        val n = NotificationPlanner.lessonNotificationsForDay(tt(listOf(e)), defaults, thursday)
            .first { it.at == at(thursday, "10:00") }
        assertEquals("Sledeći čas: Fizika · ⚠ KONTROLNI", n.title)
        assertEquals("učionica 302 · počinje u 10:15 · 3–4. čas\n⚠ KONTROLNI: Njutnovi zakoni", n.text)
        assertTrue(n.hasEvent)
    }

    @Test
    fun eventOnSecondPeriodOfBlockAndEventWithoutPeriodMatchBySubject() {
        val e1 = EventInfo(1, EventType.PISMENI, subjectId("Fizika"), "Fizika", thursday, 4)
        val e2 = EventInfo(2, EventType.USMENO, subjectId("Srpski"), "Srpski", thursday, null)
        val list = NotificationPlanner.lessonNotificationsForDay(tt(listOf(e1, e2)), defaults, thursday)
        assertTrue(list.first { it.at == at(thursday, "10:00") }.title.endsWith("PISMENI"))
        val srpski = list.first { it.at == at(thursday, "11:50") }
        assertTrue(srpski.title.endsWith("USMENO"))
        assertTrue(srpski.text.contains("⚠ USMENO na ovom času"))
        // An event for a different subject does not leak to other lessons
        assertFalse(list.first { it.at == at(thursday, "13:45") }.hasEvent)
    }

    @Test
    fun planFiltersPastAndCrossesMidnight() {
        val now = at(monday, "23:59").plusSeconds(30)
        val plan = NotificationPlanner.plan(tt(), lessonOnly(), now, horizonDays = 1)
        assertEquals(at(tuesday, "10:00"), plan.first().at)
        assertTrue(plan.all { it.at.isAfter(now) })
        assertTrue(plan.all { it.at.toLocalDate() == tuesday })
    }

    @Test
    fun planExcludesNotificationFiringRightNow() {
        // The receiver re-plans immediately after an alarm fired at 09:15:00
        val now = at(monday, "09:15").plusNanos(200_000_000)
        val plan = NotificationPlanner.plan(tt(), lessonOnly(), now, horizonDays = 0)
        assertEquals(at(monday, "10:00"), plan.first().at)
        assertEquals(5, plan.size)
    }

    @Test
    fun planFromFridayEveningJumpsToMonday() {
        val now = at(friday, "18:00")
        val plan = NotificationPlanner.plan(tt(), lessonOnly(), now, horizonDays = 7)
        assertEquals(at(monday.plusWeeks(1), "08:20"), plan.first().at)
        assertTrue(plan.none { it.at.toLocalDate().dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
        val keys = plan.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(plan.sortedBy { it.at }, plan)
    }

    @Test
    fun eventRemindersEveningBeforeAndMorning() {
        val e = EventInfo(5, EventType.KONTROLNI, subjectId("Fizika"), "Fizika", monday.plusWeeks(1), 5, "Elektrostatika")
        val s = PlannerSettings(eventEveningTime = LocalTime.of(20, 30))
        val plan = NotificationPlanner.plan(tt(listOf(e)), s, at(friday, "15:00"))
        val evening = plan.single { it.kind == NotificationKind.EVENT_EVENING }
        assertEquals(at(sunday, "20:30"), evening.at)
        assertEquals("Sutra: Kontrolni · Fizika", evening.title)
        assertEquals("5. čas u 12:10 · Elektrostatika", evening.text)
        val morning = plan.single { it.kind == NotificationKind.EVENT_MORNING }
        assertEquals(at(monday.plusWeeks(1), "07:00"), morning.at)
        assertEquals("Danas: Kontrolni · Fizika", morning.title)
    }

    @Test
    fun eventRemindersRespectToggles() {
        val e = EventInfo(5, EventType.TEST, subjectId("Fizika"), "Fizika", friday, null)
        val off = e.copy(remindersEnabled = false)
        val now = at(monday, "08:00")
        assertTrue(NotificationPlanner.plan(tt(listOf(off)), defaults, now).none { it.kind.name.startsWith("EVENT") })
        val noEvening = NotificationPlanner.plan(tt(listOf(e)), defaults.copy(eventEveningEnabled = false), now)
        assertTrue(noEvening.none { it.kind == NotificationKind.EVENT_EVENING })
        assertEquals(1, noEvening.count { it.kind == NotificationKind.EVENT_MORNING })
        val details = noEvening.single { it.kind == NotificationKind.EVENT_MORNING }.text
        assertEquals("Ne zaboravi da se spremiš 📚", details)
    }

    @Test
    fun pastEventRemindersAreNotScheduled() {
        val e = EventInfo(5, EventType.TEST, subjectId("Fizika"), "Fizika", monday, null)
        val plan = NotificationPlanner.plan(tt(listOf(e)), defaults, at(monday, "10:00"))
        assertTrue(plan.none { it.kind.name.startsWith("EVENT") })
    }

    @Test
    fun simulateAfterBlock() {
        val t = tt()
        val n = NotificationPlanner.afterBlock(t, thursday, 0)!!
        assertEquals(NotificationKind.NEXT_LESSON, n.kind)
        assertEquals("Sledeći čas: Fizika", n.title)
        val last = NotificationPlanner.afterBlock(t, thursday, t.blocksFor(thursday).lastIndex)!!
        assertEquals(NotificationKind.END_OF_DAY, last.kind)
        assertNull(NotificationPlanner.afterBlock(t, thursday, 99))
    }

    @Test
    fun changedPeriodTimesMoveNotifications() {
        val periods = base.periods.map { if (it.index == 1) it.copy(end = LocalTime.of(9, 10)) else it }
        val t = Timetable(base.copy(periods = periods))
        val list = NotificationPlanner.lessonNotificationsForDay(t, defaults, monday)
        assertNotNull(list.firstOrNull { it.at == at(monday, "09:10") })
        assertNull(list.firstOrNull { it.at == at(monday, "09:15") })
    }

    @Test
    fun emptyRoomIsOmitted() {
        val data = base.copy(lessons = base.lessons.map { if (it.subjectName == "Fizika") it.copy(room = "") else it })
        val n = NotificationPlanner.lessonNotificationsForDay(Timetable(data), defaults, thursday)
            .first { it.at == at(thursday, "10:00") }
        assertEquals("počinje u 10:15 · 3–4. čas", n.text)
    }
}
