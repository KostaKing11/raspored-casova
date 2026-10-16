package com.kostaking.raspored.core

import java.time.DayOfWeek
import java.time.LocalTime

/** The original timetable the app ships with ("Vrati originalni raspored" restores it). */
object DefaultSchedule {

    data class SeedSubject(val name: String, val color: Long)
    data class SeedLesson(val day: DayOfWeek, val startPeriod: Int, val span: Int, val subject: String, val room: String)

    val periods: List<PeriodSlot> = listOf(
        slot(0, "07:35", "08:20"),
        slot(1, "08:25", "09:15"),
        slot(2, "09:15", "10:00"),
        slot(3, "10:15", "11:00"),
        slot(4, "11:05", "11:50"),
        slot(5, "12:10", "12:55"),
        slot(6, "13:00", "13:45"),
        slot(7, "13:55", "14:40"),
    )

    /** Harmonised palette: distinct hues, Analiza and Geometrija share the blue (math) family. */
    val subjects: List<SeedSubject> = listOf(
        SeedSubject("Analiza", 0xFF4C6FFF),
        SeedSubject("Geometrija", 0xFF5AB8FF),
        SeedSubject("Fizika", 0xFF9B6BFF),
        SeedSubject("Hemija", 0xFF2BD9A6),
        SeedSubject("Srpski", 0xFFFF4D6D),
        SeedSubject("Engleski", 0xFFFF8A3D),
        SeedSubject("Istorija", 0xFFC79A6B),
        SeedSubject("Geografija", 0xFF8BD448),
        SeedSubject("Informatika", 0xFFFFC23D),
        SeedSubject("IT Vežbe", 0xFFE8E05A),
        SeedSubject("Fizičko", 0xFF2EC8E6),
        SeedSubject("Veronauka", 0xFFD06BFF),
        SeedSubject("ČOS", 0xFFFF6BC1),
    )

    val lessons: List<SeedLesson> = buildList {
        val mon = DayOfWeek.MONDAY
        add(SeedLesson(mon, 0, 1, "Engleski", "004"))
        add(SeedLesson(mon, 1, 1, "Istorija", "201"))
        add(SeedLesson(mon, 2, 1, "Srpski", "203"))
        add(SeedLesson(mon, 3, 2, "Hemija", "002"))
        add(SeedLesson(mon, 5, 1, "Geografija", "202"))
        add(SeedLesson(mon, 6, 1, "Analiza", "011"))
        add(SeedLesson(mon, 7, 1, "ČOS", "012"))

        val tue = DayOfWeek.TUESDAY
        add(SeedLesson(tue, 2, 1, "Informatika", "309"))
        add(SeedLesson(tue, 3, 2, "Analiza", "303"))
        add(SeedLesson(tue, 5, 1, "Geografija", "202"))
        add(SeedLesson(tue, 6, 1, "Hemija", "012"))

        val wed = DayOfWeek.WEDNESDAY
        add(SeedLesson(wed, 0, 2, "Informatika", "308"))
        add(SeedLesson(wed, 2, 1, "Engleski", "109"))
        add(SeedLesson(wed, 3, 1, "Istorija", "201"))
        add(SeedLesson(wed, 4, 1, "Fizičko", "108"))
        add(SeedLesson(wed, 5, 2, "Geometrija", "301"))

        val thu = DayOfWeek.THURSDAY
        add(SeedLesson(thu, 1, 2, "Geometrija", "301"))
        add(SeedLesson(thu, 3, 2, "Fizika", "302"))
        add(SeedLesson(thu, 5, 2, "Srpski", "301"))
        add(SeedLesson(thu, 7, 1, "Veronauka", "207"))

        val fri = DayOfWeek.FRIDAY
        add(SeedLesson(fri, 0, 1, "Srpski", "200"))
        add(SeedLesson(fri, 1, 1, "Fizičko", "108"))
        add(SeedLesson(fri, 2, 2, "IT Vežbe", "310"))
        add(SeedLesson(fri, 4, 1, "Analiza", "303"))
        add(SeedLesson(fri, 5, 2, "Fizika", "012"))
    }

    /** Seed data as [ScheduleData] (subject ids are 1-based positions in [subjects]). */
    fun scheduleData(): ScheduleData {
        val ids = subjects.mapIndexed { i, s -> s.name to (i + 1).toLong() }.toMap()
        return ScheduleData(
            periods = periods,
            lessons = lessons.mapIndexed { i, l ->
                LessonInfo(
                    id = (i + 1).toLong(),
                    day = l.day,
                    startPeriod = l.startPeriod,
                    span = l.span,
                    subjectId = ids.getValue(l.subject),
                    subjectName = l.subject,
                    room = l.room,
                )
            },
        )
    }

    private fun slot(index: Int, start: String, end: String) =
        PeriodSlot(index, LocalTime.parse(start), LocalTime.parse(end))
}
