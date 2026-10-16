package com.kostaking.raspored

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.data.DayOffEntity
import com.kostaking.raspored.data.EventEntity
import com.kostaking.raspored.data.LessonEntity
import com.kostaking.raspored.data.backup.Backup
import com.kostaking.raspored.data.backup.BackupException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class RepositoryAndBackupTest {

    private lateinit var c: AppContainer

    @Before
    fun setUp() {
        c = ApplicationProvider.getApplicationContext<TestApp>().container
        runBlocking { assertTrue(c.repo.seedIfEmpty()) }
    }

    @After
    fun tearDown() {
        c.db.close()
    }

    private fun mondayLessons() = runBlocking { c.repo.snapshot().lessons.filter { it.day == 1 }.sortedBy { it.startPeriod } }
    private fun subjectId(name: String) = runBlocking { c.repo.snapshot().subjects.first { it.name == name }.id }

    @Test
    fun seedIsTheOriginalTimetableAndOnlyOnce() = runBlocking {
        val s = c.repo.snapshot()
        assertEquals(13, s.subjects.size)
        assertEquals(8, s.periods.size)
        assertEquals(25, s.lessons.size)
        assertEquals(listOf("ČOS"), s.subjects.filter { it.name.startsWith("Č") }.map { it.name })
        assertFalse(c.repo.seedIfEmpty())
        assertEquals(25, c.repo.snapshot().lessons.size)
    }

    @Test
    fun savingIntoABlockShortensIt() = runBlocking {
        val geo = subjectId("Geografija")
        c.repo.saveLesson(LessonEntity(day = 1, startPeriod = 4, span = 1, subjectId = geo, room = "100"))
        val hemija = mondayLessons().first { it.startPeriod == 3 }
        assertEquals(1, hemija.span)
        assertEquals("100", mondayLessons().first { it.startPeriod == 4 }.room)
    }

    @Test
    fun mergeAndSplit() = runBlocking {
        val engleski = mondayLessons().first { it.startPeriod == 0 }
        assertTrue(c.repo.mergeWithNext(engleski.id))
        val merged = mondayLessons()
        assertEquals(2, merged.first { it.startPeriod == 0 }.span)
        assertTrue(merged.none { it.startPeriod == 1 }) // Istorija replaced by the block

        val hemija = merged.first { it.startPeriod == 3 }
        c.repo.splitBlock(hemija.id)
        val split = mondayLessons()
        assertEquals(1, split.first { it.startPeriod == 3 }.span)
        val fourth = split.first { it.startPeriod == 4 }
        assertEquals(hemija.subjectId, fourth.subjectId)
        assertEquals("002", fourth.room)

        // the last period cannot be merged further
        val cos = split.first { it.startPeriod == 7 }
        assertFalse(c.repo.mergeWithNext(cos.id))
    }

    @Test
    fun removingLastPeriodRemovesItsLessons() = runBlocking {
        c.repo.removeLastPeriod()
        val s = c.repo.snapshot()
        assertEquals(7, s.periods.size)
        assertTrue(s.lessons.none { it.startPeriod == 7 })
        c.repo.addPeriod()
        val added = c.repo.snapshot().periods.last()
        assertEquals(7, added.idx)
        assertEquals(13 * 60 + 50, added.startMin) // 5 min after 13:45
    }

    @Test
    fun resetKeepsEventsAndRestoresLessons() = runBlocking {
        val fizika = subjectId("Fizika")
        c.repo.saveEvent(EventEntity(type = "KONTROLNI", subjectId = fizika, date = LocalDate.of(2026, 10, 15).toEpochDay(), periodIndex = 3))
        mondayLessons().forEach { c.repo.deleteLesson(it.id) }
        c.repo.saveSubject(c.repo.snapshot().subjects.first { it.id == fizika }.copy(color = 0xFF000000.toInt()))
        c.repo.resetToOriginal()
        val s = c.repo.snapshot()
        assertEquals(25, s.lessons.size)
        assertEquals(1, s.events.size)
        assertEquals(fizika, s.events.single().subjectId)
        assertEquals(0xFF9B6BFF.toInt(), s.subjects.first { it.id == fizika }.color)
    }

    @Test
    fun subjectInUseCannotBeDeleted() = runBlocking {
        assertFalse(c.repo.deleteSubject(subjectId("Fizika")))
        val id = c.repo.subjectByNameOrCreate("Likovno", 0xFF123456.toInt())
        assertEquals(id, c.repo.subjectByNameOrCreate("likovno ", 0))
        assertTrue(c.repo.deleteSubject(id))
    }

    @Test
    fun backupRoundTrip() = runBlocking {
        val fizika = subjectId("Fizika")
        c.repo.saveEvent(EventEntity(type = EventType.PISMENI.name, subjectId = fizika, date = LocalDate.of(2026, 11, 3).toEpochDay(), periodIndex = 5, note = "Kinematika, čćšžđ"))
        c.repo.saveDayOff(DayOffEntity(startDate = LocalDate.of(2026, 12, 30).toEpochDay(), endDate = LocalDate.of(2027, 1, 15).toEpochDay(), label = "Zimski raspust"))
        val before = c.repo.snapshot()
        val json = Backup.export(before)
        assertTrue(json.contains("\"Kinematika, čćšžđ\""))
        assertTrue(json.contains("\"07:35\""))

        c.repo.resetToOriginal()
        mondayLessons().forEach { c.repo.deleteLesson(it.id) }
        Backup.import(c.repo, json)
        val after = c.repo.snapshot()

        fun norm(s: com.kostaking.raspored.data.ScheduleState) = listOf(
            s.subjects.map { Triple(it.id, it.name, it.color) }.sortedBy { it.first },
            s.periods,
            s.lessons.map { listOf(it.day, it.startPeriod, it.span, it.subjectId, it.room) }.sortedBy { it.toString() },
            s.events.map { listOf(it.type, it.subjectId, it.date, it.periodIndex, it.note, it.reminders) },
            s.daysOff.map { listOf(it.startDate, it.endDate, it.label) },
        )
        assertEquals(norm(before), norm(after))
    }

    @Test
    fun invalidBackupIsRejected() {
        assertThrows(BackupException::class.java) { Backup.parse("{nije json") }
        assertThrows(BackupException::class.java) { Backup.parse("""{"app":"nesto-drugo","periods":[{"index":0,"start":"08:00","end":"08:45"}]}""") }
        assertThrows(BackupException::class.java) {
            Backup.parse(
                """{"app":"raspored-casova","subjects":[],"periods":[{"index":0,"start":"08:00","end":"08:45"}],
                   "lessons":[{"day":1,"period":0,"subjectId":5}]}""",
            )
        }
    }
}
