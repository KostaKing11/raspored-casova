package com.kostaking.raspored

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.core.NotificationKind
import com.kostaking.raspored.core.SchoolDayPager
import com.kostaking.raspored.data.AppSettings
import com.kostaking.raspored.data.EventEntity
import com.kostaking.raspored.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * End-to-end UI tour on a real emulator. Also stores screenshots in the app's files dir
 * (pulled by CI with `run-as`).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx = instrumentation.targetContext
    private val c get() = ctx.container

    private val resetData = object : ExternalResource() {
        override fun before() = runBlocking {
            c.repo.snapshot().events.forEach { c.repo.deleteEvent(it.id) }
            c.repo.snapshot().daysOff.forEach { c.repo.deleteDayOff(it.id) }
            c.repo.resetToOriginal()
            c.settings.update { AppSettings(onboardingDone = true, samsungStepDone = true) }
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(resetData)
        .around(compose)

    private val today: LocalDate get() = LocalDate.now()
    private val shownDay: LocalDate get() = SchoolDayPager.schoolDayOnOrAfter(today)

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.filesDir, "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String, timeout: Long = 5_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun inDay(date: LocalDate, tag: String): SemanticsNodeInteraction =
        compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("day-$date")))

    private fun firstPeriodOf(date: LocalDate): Int =
        runBlocking { c.repo.snapshot() }.timetable.blocksFor(date).first().firstPeriod

    @Test
    fun a01_dayScreenOpensOnTodayOrMonday() {
        waitText(Format.dayTitle(shownDay))
        compose.onNodeWithText(Format.dayTitle(shownDay)).assertExists()
        if (SchoolDayPager.isWeekend(today)) {
            compose.onNodeWithText("Vikend, sledeći je ponedeljak").assertExists()
        } else {
            compose.onNodeWithText("Danas").assertExists()
        }
        inDay(shownDay, "lesson-${firstPeriodOf(shownDay)}").assertExists()
        shot("01_dan_tamna_tema")
    }

    @Test
    fun a02_swipeMovesBetweenSchoolDays() {
        waitText(Format.dayTitle(shownDay))
        val page = SchoolDayPager.pageFor(shownDay)
        val next = SchoolDayPager.dateFor(page + 1)
        val prev = SchoolDayPager.dateFor(page - 1)

        compose.onNodeWithTag("dayPager").performTouchInput { swipeLeft() }
        waitText(Format.dayTitle(next))
        inDay(next, "lesson-${firstPeriodOf(next)}").assertExists()
        shot("02_swipe_sledeci_dan")

        compose.onNodeWithTag("todayButton").performClick()
        waitText(Format.dayTitle(shownDay))

        compose.onNodeWithTag("dayPager").performTouchInput { swipeRight() }
        waitText(Format.dayTitle(prev))
        compose.onNodeWithTag("todayButton").performClick()
        waitText(Format.dayTitle(shownDay))
    }

    @Test
    fun a03_addTestWithLongPressShowsBadge() {
        waitText(Format.dayTitle(shownDay))
        val period = firstPeriodOf(shownDay)
        inDay(shownDay, "lesson-$period").performTouchInput { longClick() }
        compose.onNodeWithText("Dodaj kontrolni za ovaj čas").performClick()
        waitText("Nova obaveza")
        compose.onNodeWithTag("note").performTextInput("Gradivo: prve tri lekcije")
        shot("03_dodaj_kontrolni")
        compose.onNodeWithTag("saveEvent").performClick()
        compose.waitUntil(5_000) {
            // the card is clickable, which merges its children – look in the unmerged tree
            compose.onAllNodes(hasTestTag("badge-$period") and hasAnyAncestor(hasTestTag("day-$shownDay")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val e = runBlocking { c.repo.snapshot() }.events.single()
        assertEquals(shownDay.toEpochDay(), e.date)
        assertEquals(period, e.periodIndex)
        assertEquals(EventType.KONTROLNI.name, e.type)
        assertEquals("Gradivo: prve tri lekcije", e.note)
        shot("04_kartica_sa_kontrolnim")
    }

    @Test
    fun a04_calendarAndUpcoming() {
        val state = runBlocking { c.repo.snapshot() }
        val fizika = state.subjects.first { it.name == "Fizika" }.id
        val geo = state.subjects.first { it.name == "Geometrija" }.id
        val d1 = SchoolDayPager.dateFor(SchoolDayPager.pageFor(today) + 3)
        val d2 = SchoolDayPager.dateFor(SchoolDayPager.pageFor(today) + 6)
        runBlocking {
            c.repo.saveEvent(EventEntity(type = "KONTROLNI", subjectId = fizika, date = d1.toEpochDay(), periodIndex = null, note = "Optika"))
            c.repo.saveEvent(EventEntity(type = "TEST", subjectId = geo, date = d2.toEpochDay(), periodIndex = null, note = "Vektori"))
        }
        compose.onNodeWithTag("tabCalendar").performClick()
        waitText(Format.monthTitle(today.year, today.monthValue))
        shot("05_kalendar")
        compose.onNodeWithTag("tabUpcoming").performClick()
        waitText("Kontrolni · Fizika")
        compose.onNodeWithText(Format.relativeDay(today, d1)).assertExists()
        shot("06_predstojece")

        // Tap on a weekday in the calendar opens that day
        compose.onNodeWithTag("tabCalendar").performClick()
        val target = if (d1.monthValue == today.monthValue) d1 else shownDay
        compose.onNodeWithTag("cal-$target").performClick()
        waitText(Format.dayTitle(target))
    }

    @Test
    fun a05_editScheduleAndSettingsScreens() {
        compose.onNodeWithTag("tabSettings").performClick()
        waitText("Podešavanja")
        shot("07_podesavanja")
        compose.onNodeWithTag("navEditSchedule").performClick()
        waitText("Uredi raspored")
        compose.onNodeWithTag("tab-1").performClick()
        shot("08_uredi_raspored")
        compose.onNodeWithTag("lessonRow-0").performClick()
        compose.onNodeWithTag("room").performTextClearance()
        compose.onNodeWithTag("room").performTextInput("105")
        compose.onNodeWithTag("saveLesson").performClick()
        compose.waitUntil(5_000) {
            runBlocking { c.repo.snapshot() }.lessons.any { it.day == 1 && it.startPeriod == 0 && it.room == "105" }
        }
        compose.onNodeWithTag("back").performClick()

        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("navSubjects"))
        compose.onNodeWithTag("navSubjects").performClick()
        waitText("Predmeti i boje")
        shot("09_predmeti_boje")
        compose.onNodeWithTag("back").performClick()

        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("navPeriods"))
        compose.onNodeWithTag("navPeriods").performClick()
        waitText("Termini časova")
        shot("10_termini")
        compose.onNodeWithTag("back").performClick()

        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("navDebug"))
        compose.onNodeWithTag("navDebug").performClick()
        waitText("Debug / test")
        shot("11_debug")
        compose.onNodeWithTag("back").performClick()

        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("navPermissions"))
        compose.onNodeWithTag("navPermissions").performClick()
        waitText("Dozvole i baterija")
        shot("12_dozvole")
    }

    @Test
    fun a06_lightTheme() {
        c.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        waitText(Format.dayTitle(shownDay))
        shot("13_dan_svetla_tema")
    }

    @Test
    fun a07_exactAlarmIsDeliveredOnTime() {
        val r = runBlocking { c.scheduler.reschedule("instrumented") }
        assertTrue("alarms must be exact on the emulator", r.exact)
        val am = ctx.getSystemService(AlarmManager::class.java)
        assertNotNull("an AlarmClock must be registered", am.nextAlarmClock)

        runBlocking { c.db.alarmDao().clearLog() }
        val at = runBlocking { c.scheduler.scheduleTest(5) }
        compose.waitUntil(30_000) { runBlocking { c.db.alarmDao().log() }.any { it.kind == NotificationKind.TEST.name } }
        val entry = runBlocking { c.db.alarmDao().log() }.first { it.kind == NotificationKind.TEST.name }
        assertEquals(at, entry.scheduledAt)
        val delay = entry.deliveredAt - entry.scheduledAt
        android.util.Log.i("RasporedTest", "test alarm delay=${delay}ms")
        assertTrue("delivered ${delay}ms late", delay in -50..3_000)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue(nm.activeNotifications.any { it.notification.extras.getString("android.title")?.startsWith("Test notifikacija") == true })
    }
}
