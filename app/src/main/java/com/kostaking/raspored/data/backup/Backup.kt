package com.kostaking.raspored.data.backup

import com.kostaking.raspored.data.DayOffEntity
import com.kostaking.raspored.data.EventEntity
import com.kostaking.raspored.data.LessonEntity
import com.kostaking.raspored.data.PeriodEntity
import com.kostaking.raspored.data.ScheduleRepository
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.data.SubjectEntity
import com.kostaking.raspored.core.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val version: Int = 1,
    val exportedAt: String = "",
    val subjects: List<BSubject> = emptyList(),
    val periods: List<BPeriod> = emptyList(),
    val lessons: List<BLesson> = emptyList(),
    val events: List<BEvent> = emptyList(),
    val daysOff: List<BDayOff> = emptyList(),
) {
    companion object {
        const val APP_ID = "raspored-casova"
    }
}

@Serializable
data class BSubject(val id: Long, val name: String, val color: String)

@Serializable
data class BPeriod(val index: Int, val start: String, val end: String)

@Serializable
data class BLesson(
    /** 1 = ponedeljak … 5 = petak */
    val day: Int,
    val period: Int,
    val span: Int = 1,
    val subjectId: Long,
    val room: String = "",
)

@Serializable
data class BEvent(
    val type: String,
    val subjectId: Long,
    val date: String,
    val period: Int? = null,
    val note: String = "",
    val reminders: Boolean = true,
)

@Serializable
data class BDayOff(val start: String, val end: String, val label: String = "")

class BackupException(message: String) : Exception(message)

object Backup {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(state: ScheduleState, now: LocalDateTime = LocalDateTime.now()): String {
        val file = BackupFile(
            exportedAt = now.withNano(0).toString(),
            subjects = state.subjects.map { BSubject(it.id, it.name, colorToHex(it.color)) },
            periods = state.periods.map { BPeriod(it.idx, hhmm(it.startMin), hhmm(it.endMin)) },
            lessons = state.lessons.map { BLesson(it.day, it.startPeriod, it.span, it.subjectId, it.room) },
            events = state.events.map {
                BEvent(it.type, it.subjectId, LocalDate.ofEpochDay(it.date).toString(), it.periodIndex, it.note, it.reminders)
            },
            daysOff = state.daysOff.map {
                BDayOff(LocalDate.ofEpochDay(it.startDate).toString(), LocalDate.ofEpochDay(it.endDate).toString(), it.label)
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Parses and validates a backup. Throws [BackupException] with a user-readable message. */
    fun parse(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("Fajl nije ispravan JSON backup rasporeda.")
        }
        if (file.app != BackupFile.APP_ID) throw BackupException("Ovo nije backup aplikacije Raspored časova.")
        if (file.periods.isEmpty()) throw BackupException("Backup nema termine časova.")
        val ids = file.subjects.map { it.id }.toSet()
        if (ids.size != file.subjects.size) throw BackupException("Backup ima duplirane predmete.")
        file.lessons.forEach {
            if (it.subjectId !in ids) throw BackupException("Čas se odnosi na nepostojeći predmet.")
            if (it.day !in 1..7 || it.span < 1) throw BackupException("Neispravan čas u backup-u.")
        }
        file.events.forEach { if (it.subjectId !in ids) throw BackupException("Kontrolni se odnosi na nepostojeći predmet.") }
        try {
            file.periods.forEach { LocalTime.parse(it.start); LocalTime.parse(it.end) }
            file.events.forEach { LocalDate.parse(it.date) }
            file.daysOff.forEach { LocalDate.parse(it.start); LocalDate.parse(it.end) }
            file.subjects.forEach { parseColor(it.color) }
        } catch (e: Exception) {
            throw BackupException("Backup sadrži neispravan datum, vreme ili boju.")
        }
        return file
    }

    suspend fun import(repo: ScheduleRepository, text: String) {
        val f = parse(text)
        repo.replaceAll(
            subjects = f.subjects.map { SubjectEntity(it.id, it.name, parseColor(it.color)) },
            periods = f.periods.map {
                PeriodEntity(it.index, toMinutes(LocalTime.parse(it.start)), toMinutes(LocalTime.parse(it.end)))
            },
            lessons = f.lessons.map { LessonEntity(0, it.day, it.period, it.span, it.subjectId, it.room) },
            events = f.events.map {
                EventEntity(0, EventType.fromName(it.type).name, it.subjectId, LocalDate.parse(it.date).toEpochDay(), it.period, it.note, it.reminders)
            },
            daysOff = f.daysOff.map {
                DayOffEntity(0, LocalDate.parse(it.start).toEpochDay(), LocalDate.parse(it.end).toEpochDay(), it.label)
            },
        )
    }

    private fun toMinutes(t: LocalTime) = t.hour * 60 + t.minute
    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    fun colorToHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun parseColor(hex: String): Int {
        val clean = hex.removePrefix("#")
        require(clean.length == 6 || clean.length == 8)
        val v = clean.toLong(16)
        return if (clean.length == 6) (0xFF000000 or v).toInt() else v.toInt()
    }
}
