package com.kostaking.raspored

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kostaking.raspored.alarm.AlarmReceiver
import com.kostaking.raspored.alarm.AlarmScheduler
import com.kostaking.raspored.core.NotificationKind
import com.kostaking.raspored.core.NotificationPlanner
import com.kostaking.raspored.data.AlarmMode
import com.kostaking.raspored.data.DayOffEntity
import com.kostaking.raspored.data.EventEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class AlarmSchedulingTest {

    private val app: TestApp get() = ApplicationProvider.getApplicationContext()
    private lateinit var c: AppContainer
    private val am get() = app.getSystemService(AlarmManager::class.java)

    // Monday 2026-10-12, 08:00
    private val monday = LocalDate.of(2026, 10, 12)
    private var now: LocalDateTime = monday.atTime(8, 0)
    private lateinit var scheduler: AlarmScheduler
    private val originalTz: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Belgrade"))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        c = app.container
        runBlocking { c.repo.seedIfEmpty() }
        scheduler = AlarmScheduler(app, c.repo, c.settings, c.db.alarmDao(), clock = { now })
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
        c.db.close()
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun schedulesExactAlarmClocksForTheNextWeek() = runBlocking {
        val r = scheduler.reschedule("test")
        val expected = NotificationPlanner.plan(c.repo.snapshot().timetable, c.settings.current.planner(), now)
        assertEquals(expected.size, r.count)
        assertTrue(r.exact)

        val rows = c.db.alarmDao().scheduled().filter { it.requestCode < AlarmScheduler.FIRST_ONE_OFF_CODE }
        assertEquals(expected.size, rows.size)
        assertEquals(millis(monday.atTime(8, 20)), rows.first().triggerAt)
        assertEquals(NotificationKind.NEXT_LESSON.name, rows.first().kind)
        assertEquals("Sledeći čas: Istorija", rows.first().title)
        assertTrue(rows.all { it.exact })
        // every trigger is on a whole minute
        assertTrue(rows.all { it.triggerAt % 60_000 == 0L })

        // AlarmManager knows the earliest one as an alarm clock
        assertEquals(millis(monday.atTime(8, 20)), am.nextAlarmClock?.triggerTime)

        // and a PendingIntent exists for each planned alarm
        rows.forEach { row ->
            val pi = PendingIntent.getBroadcast(
                app, row.requestCode,
                Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).setData(Uri.parse("raspored://alarm/${row.requestCode}")),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            assertNotNull("missing PendingIntent for ${row.requestCode}", pi)
        }
    }

    @Test
    fun reschedulingReplacesOldAlarms() = runBlocking {
        scheduler.reschedule("first")
        val firstCount = c.db.alarmDao().scheduled().size
        // Monday becomes a holiday -> first alarm moves to Tuesday 10:00
        c.repo.saveDayOff(DayOffEntity(startDate = monday.toEpochDay(), endDate = monday.toEpochDay(), label = "Praznik"))
        scheduler.reschedule("second")
        val rows = c.db.alarmDao().scheduled()
        assertTrue(rows.size < firstCount)
        assertEquals(millis(monday.plusDays(1).atTime(10, 0)), rows.first().triggerAt)
        // the cancelled Monday alarm clock must be gone (Robolectric's ordering of the
        // remaining alarm clocks is not reliable, the emulator test checks the real system)
        assertTrue(am.nextAlarmClock?.triggerTime != millis(monday.atTime(8, 20)))
        // codes are reused from 1000 upward, so nothing stale is left behind
        assertEquals((0 until rows.size).map { AlarmScheduler.FIRST_PLAN_CODE + it }, rows.map { it.requestCode })
        val stale = PendingIntent.getBroadcast(
            app, AlarmScheduler.FIRST_PLAN_CODE + firstCount - 1,
            Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
                .setData(Uri.parse("raspored://alarm/${AlarmScheduler.FIRST_PLAN_CODE + firstCount - 1}")),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        assertNull(stale)
    }

    @Test
    fun exactIdleModeDoesNotUseAlarmClock() = runBlocking {
        c.settings.update { it.copy(alarmMode = AlarmMode.EXACT_IDLE) }
        scheduler.reschedule("test")
        assertNull(am.nextAlarmClock)
        assertTrue(c.db.alarmDao().scheduled().isNotEmpty())
    }

    @Test
    fun withoutExactPermissionAlarmsAreMarkedInexact() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val r = scheduler.reschedule("test")
        assertFalse(r.exact)
        assertTrue(c.db.alarmDao().scheduled().none { it.exact })
    }

    @Test
    fun timezoneChangeMovesAbsoluteTriggerTimes() = runBlocking {
        scheduler.reschedule("tz1")
        val belgrade = c.db.alarmDao().scheduled().first().triggerAt
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        scheduler.reschedule("tz2")
        val london = c.db.alarmDao().scheduled().first().triggerAt
        assertEquals(60 * 60 * 1000L, london - belgrade) // 08:20 local is one hour later in absolute time
    }

    @Test
    fun weekendPlanStartsMonday() = runBlocking {
        now = monday.minusDays(2).atTime(12, 0) // Saturday
        scheduler.reschedule("weekend")
        assertEquals(millis(monday.atTime(8, 20)), c.db.alarmDao().scheduled().first().triggerAt)
    }

    @Test
    fun eventShowsUpInScheduledNotification() = runBlocking {
        val fizika = c.repo.snapshot().subjects.first { it.name == "Fizika" }.id
        c.repo.saveEvent(EventEntity(type = "KONTROLNI", subjectId = fizika, date = monday.plusDays(3).toEpochDay(), periodIndex = 3, note = "Optika"))
        scheduler.reschedule("event")
        val rows = c.db.alarmDao().scheduled()
        val thursday10 = rows.first { it.triggerAt == millis(monday.plusDays(3).atTime(10, 0)) }
        assertEquals("Sledeći čas: Fizika · ⚠ KONTROLNI", thursday10.title)
        assertTrue(rows.any { it.kind == NotificationKind.EVENT_EVENING.name && it.triggerAt == millis(monday.plusDays(2).atTime(19, 0)) })
        assertTrue(rows.any { it.kind == NotificationKind.EVENT_MORNING.name && it.triggerAt == millis(monday.plusDays(3).atTime(7, 0)) })
    }

    @Test
    fun receiverPostsNotificationLogsDelayAndReschedules() {
        shadowOf(app as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val trigger = System.currentTimeMillis() - 1500
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_CODE, 1000)
            .putExtra(AlarmReceiver.EXTRA_KIND, NotificationKind.NEXT_LESSON.name)
            .putExtra(AlarmReceiver.EXTRA_TITLE, "Sledeći čas: Fizika")
            .putExtra(AlarmReceiver.EXTRA_TEXT, "učionica 302 · počinje u 10:15")
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, trigger)
        val latch = CountDownLatch(1)
        AlarmReceiver.handle(app, intent) { latch.countDown() }
        assertTrue(latch.await(10, TimeUnit.SECONDS))

        val nm = app.getSystemService(NotificationManager::class.java)
        val posted = nm.activeNotifications.single()
        assertEquals("Sledeći čas: Fizika", posted.notification.extras.getString("android.title"))
        assertEquals("lessons", posted.notification.channelId)

        val log = runBlocking { c.db.alarmDao().log() }.single()
        assertEquals(trigger, log.scheduledAt)
        assertTrue(log.deliveredAt - log.scheduledAt >= 1500)
    }

    @Test
    fun testNotificationIsScheduledOneMinuteAhead() = runBlocking {
        val before = System.currentTimeMillis()
        val at = scheduler.scheduleTest(60)
        assertTrue(at - before in 59_000..61_000)
        val row = c.db.alarmDao().scheduled().single { it.requestCode == AlarmScheduler.CODE_TEST }
        assertEquals(NotificationKind.TEST.name, row.kind)
        // a full reschedule keeps the one-off test alarm
        scheduler.reschedule("keep")
        assertTrue(c.db.alarmDao().scheduled().any { it.requestCode == AlarmScheduler.CODE_TEST })
    }

    @Test
    fun simulatedEndOfLessonUsesRealText() = runBlocking {
        val at = scheduler.scheduleSimulated(monday.plusDays(3), 0, 60)
        assertNotNull(at)
        val row = c.db.alarmDao().scheduled().single { it.requestCode == AlarmScheduler.CODE_SIMULATED }
        assertEquals("Sledeći čas: Fizika", row.title)
        assertEquals("učionica 302 · počinje u 10:15 · 3–4. čas", row.text)
    }
}
