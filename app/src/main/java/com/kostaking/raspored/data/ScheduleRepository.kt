package com.kostaking.raspored.data

import androidx.room.withTransaction
import com.kostaking.raspored.core.DayOffInfo
import com.kostaking.raspored.core.DefaultSchedule
import com.kostaking.raspored.core.EventInfo
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.core.LessonInfo
import com.kostaking.raspored.core.PeriodSlot
import com.kostaking.raspored.core.ScheduleData
import com.kostaking.raspored.core.Timetable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Snapshot of everything the UI and the alarm planner need. */
data class ScheduleState(
    val subjects: List<SubjectEntity>,
    val periods: List<PeriodEntity>,
    val lessons: List<LessonEntity>,
    val events: List<EventEntity>,
    val daysOff: List<DayOffEntity>,
) {
    val subjectById: Map<Long, SubjectEntity> by lazy { subjects.associateBy { it.id } }

    fun color(subjectId: Long): Int = subjectById[subjectId]?.color ?: 0xFF8899AA.toInt()
    fun subjectName(subjectId: Long): String = subjectById[subjectId]?.name ?: "?"

    val data: ScheduleData by lazy {
        ScheduleData(
            periods = periods.map { PeriodSlot(it.idx, minutesToTime(it.startMin), minutesToTime(it.endMin)) },
            lessons = lessons.mapNotNull { l ->
                if (l.day !in 1..7) return@mapNotNull null
                LessonInfo(
                    id = l.id,
                    day = DayOfWeek.of(l.day),
                    startPeriod = l.startPeriod,
                    span = l.span,
                    subjectId = l.subjectId,
                    subjectName = subjectName(l.subjectId),
                    room = l.room,
                )
            },
            events = events.map { it.toInfo(subjectName(it.subjectId)) },
            daysOff = daysOff.map { DayOffInfo(it.id, LocalDate.ofEpochDay(it.startDate), LocalDate.ofEpochDay(it.endDate), it.label) },
        )
    }

    val timetable: Timetable by lazy { Timetable(data) }

    companion object {
        val EMPTY = ScheduleState(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

fun minutesToTime(m: Int): LocalTime = LocalTime.of((m / 60).coerceIn(0, 23), m % 60)
fun timeToMinutes(t: LocalTime): Int = t.hour * 60 + t.minute

fun EventEntity.toInfo(subjectName: String) = EventInfo(
    id = id,
    type = EventType.fromName(type),
    subjectId = subjectId,
    subjectName = subjectName,
    date = LocalDate.ofEpochDay(date),
    periodIndex = periodIndex,
    note = note,
    remindersEnabled = reminders,
)

class ScheduleRepository(private val db: AppDatabase) {
    private val dao = db.scheduleDao()

    val state: Flow<ScheduleState> = combine(
        dao.subjectsFlow(),
        dao.periodsFlow(),
        dao.lessonsFlow(),
        dao.eventsFlow(),
        dao.daysOffFlow(),
    ) { s, p, l, e, d -> ScheduleState(s, p, l, e, d) }

    suspend fun snapshot(): ScheduleState = ScheduleState(
        dao.subjects(), dao.periods(), dao.lessons(), dao.events(), dao.daysOff(),
    )

    /** Inserts the original timetable on first start. Returns true if it seeded. */
    suspend fun seedIfEmpty(): Boolean = db.withTransaction {
        if (dao.periods().isNotEmpty() || dao.subjects().isNotEmpty()) return@withTransaction false
        insertDefaults(keepExistingSubjects = false)
        true
    }

    /** "Vrati originalni raspored": restores periods, lessons and subject colours. Keeps events and days off. */
    suspend fun resetToOriginal() = db.withTransaction {
        dao.clearLessons()
        dao.clearPeriods()
        insertDefaults(keepExistingSubjects = true)
    }

    private suspend fun insertDefaults(keepExistingSubjects: Boolean) {
        DefaultSchedule.periods.forEach {
            dao.upsertPeriod(PeriodEntity(it.index, timeToMinutes(it.start), timeToMinutes(it.end)))
        }
        val existing = if (keepExistingSubjects) dao.subjects() else emptyList()
        val ids = mutableMapOf<String, Long>()
        DefaultSchedule.subjects.forEach { seed ->
            val match = existing.firstOrNull { it.name.equals(seed.name, ignoreCase = true) }
            ids[seed.name] = if (match != null) {
                dao.updateSubject(match.copy(name = seed.name, color = seed.color.toInt()))
                match.id
            } else {
                dao.insertSubject(SubjectEntity(name = seed.name, color = seed.color.toInt()))
            }
        }
        DefaultSchedule.lessons.forEach { l ->
            dao.insertLesson(
                LessonEntity(
                    day = l.day.value,
                    startPeriod = l.startPeriod,
                    span = l.span,
                    subjectId = ids.getValue(l.subject),
                    room = l.room,
                ),
            )
        }
    }

    // ---- Subjects ----

    suspend fun saveSubject(subject: SubjectEntity): Long =
        if (subject.id == 0L) dao.insertSubject(subject) else subject.id.also { dao.updateSubject(subject) }

    /** Returns the existing subject with that name, or creates it with [color]. */
    suspend fun subjectByNameOrCreate(name: String, color: Int): Long {
        val trimmed = name.trim()
        dao.subjects().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        return dao.insertSubject(SubjectEntity(name = trimmed, color = color))
    }

    /** Deletes the subject only if nothing uses it. Returns false when it is still in use. */
    suspend fun deleteSubject(id: Long): Boolean = db.withTransaction {
        if (dao.lessonCount(id) > 0 || dao.eventCount(id) > 0) return@withTransaction false
        dao.deleteSubject(id)
        true
    }

    // ---- Periods ----

    suspend fun savePeriod(period: PeriodEntity) = dao.upsertPeriod(period)

    suspend fun addPeriod() = db.withTransaction {
        val periods = dao.periods()
        val last = periods.lastOrNull()
        val start = (last?.endMin ?: (8 * 60 - 5)) + 5
        val end = (start + 45).coerceAtMost(23 * 60 + 59)
        dao.upsertPeriod(PeriodEntity((last?.idx ?: -1) + 1, start.coerceAtMost(end - 1), end))
    }

    /** Removes the last period; lessons using it are shortened or removed. */
    suspend fun removeLastPeriod() = db.withTransaction {
        val last = dao.periods().lastOrNull() ?: return@withTransaction
        dao.deletePeriod(last.idx)
        dao.lessons().forEach { l ->
            val end = l.startPeriod + l.span - 1
            when {
                l.startPeriod >= last.idx -> dao.deleteLesson(l.id)
                end >= last.idx -> dao.updateLesson(l.copy(span = last.idx - l.startPeriod))
            }
        }
    }

    // ---- Lessons ----

    /**
     * Saves a lesson occupying [startPeriod] .. startPeriod+span-1 on [day]. Other lessons that
     * overlap that range are shortened (if they start earlier) or removed.
     */
    suspend fun saveLesson(lesson: LessonEntity): Long = db.withTransaction {
        val a = lesson.startPeriod
        val b = lesson.startPeriod + lesson.span - 1
        dao.lessonsForDay(lesson.day).filter { it.id != lesson.id }.forEach { other ->
            val oa = other.startPeriod
            val ob = other.startPeriod + other.span - 1
            if (oa <= b && ob >= a) {
                if (oa < a) dao.updateLesson(other.copy(span = a - oa)) else dao.deleteLesson(other.id)
            }
        }
        if (lesson.id == 0L) dao.insertLesson(lesson) else lesson.id.also { dao.updateLesson(lesson) }
    }

    suspend fun deleteLesson(id: Long) = dao.deleteLesson(id)

    /** Extends a lesson by one period, turning it into a block ("spoji u blok"). */
    suspend fun mergeWithNext(id: Long): Boolean {
        val l = dao.lesson(id) ?: return false
        val nextIdx = l.startPeriod + l.span
        if (dao.periods().none { it.idx == nextIdx }) return false
        saveLesson(l.copy(span = l.span + 1))
        return true
    }

    /** Splits a block into single lessons with the same subject and room. */
    suspend fun splitBlock(id: Long) = db.withTransaction {
        val l = dao.lesson(id) ?: return@withTransaction
        if (l.span <= 1) return@withTransaction
        dao.updateLesson(l.copy(span = 1))
        for (k in 1 until l.span) {
            dao.insertLesson(l.copy(id = 0, startPeriod = l.startPeriod + k, span = 1))
        }
    }

    // ---- Events ----

    suspend fun saveEvent(event: EventEntity): Long = dao.insertEvent(event)
    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    // ---- Days off ----

    suspend fun saveDayOff(dayOff: DayOffEntity): Long = dao.insertDayOff(dayOff)
    suspend fun deleteDayOff(id: Long) = dao.deleteDayOff(id)

    // ---- Backup ----

    suspend fun replaceAll(
        subjects: List<SubjectEntity>,
        periods: List<PeriodEntity>,
        lessons: List<LessonEntity>,
        events: List<EventEntity>,
        daysOff: List<DayOffEntity>,
    ) = db.withTransaction {
        dao.clearLessons()
        dao.clearEvents()
        dao.clearDaysOff()
        dao.clearPeriods()
        dao.clearSubjects()
        subjects.forEach { dao.insertSubject(it) }
        periods.forEach { dao.upsertPeriod(it) }
        lessons.forEach { dao.insertLesson(it.copy(id = 0)) }
        events.forEach { dao.insertEvent(it.copy(id = 0)) }
        daysOff.forEach { dao.insertDayOff(it.copy(id = 0)) }
    }
}
