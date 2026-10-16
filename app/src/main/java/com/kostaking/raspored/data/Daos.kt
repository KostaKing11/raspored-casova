package com.kostaking.raspored.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    // Subjects
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    fun subjectsFlow(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    suspend fun subjects(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubject(id: Long)

    @Query("SELECT COUNT(*) FROM lessons WHERE subjectId = :id")
    suspend fun lessonCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM events WHERE subjectId = :id")
    suspend fun eventCount(id: Long): Int

    // Periods
    @Query("SELECT * FROM periods ORDER BY idx")
    fun periodsFlow(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY idx")
    suspend fun periods(): List<PeriodEntity>

    @Upsert
    suspend fun upsertPeriod(period: PeriodEntity)

    @Query("DELETE FROM periods WHERE idx = :idx")
    suspend fun deletePeriod(idx: Int)

    // Lessons
    @Query("SELECT * FROM lessons ORDER BY day, startPeriod")
    fun lessonsFlow(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY day, startPeriod")
    suspend fun lessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE day = :day ORDER BY startPeriod")
    suspend fun lessonsForDay(day: Int): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun lesson(id: Long): LessonEntity?

    @Insert
    suspend fun insertLesson(lesson: LessonEntity): Long

    @Update
    suspend fun updateLesson(lesson: LessonEntity)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteLesson(id: Long)

    // Events
    @Query("SELECT * FROM events ORDER BY date, periodIndex")
    fun eventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY date, periodIndex")
    suspend fun events(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    // Days off
    @Query("SELECT * FROM days_off ORDER BY startDate")
    fun daysOffFlow(): Flow<List<DayOffEntity>>

    @Query("SELECT * FROM days_off ORDER BY startDate")
    suspend fun daysOff(): List<DayOffEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayOff(dayOff: DayOffEntity): Long

    @Query("DELETE FROM days_off WHERE id = :id")
    suspend fun deleteDayOff(id: Long)

    // Bulk
    @Query("DELETE FROM lessons") suspend fun clearLessons()
    @Query("DELETE FROM periods") suspend fun clearPeriods()
    @Query("DELETE FROM subjects") suspend fun clearSubjects()
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM days_off") suspend fun clearDaysOff()
}

@Dao
interface AlarmDao {
    @Insert
    suspend fun insertLog(entry: NotifLogEntity)

    @Query("SELECT * FROM notif_log ORDER BY deliveredAt DESC LIMIT 200")
    fun logFlow(): Flow<List<NotifLogEntity>>

    @Query("SELECT * FROM notif_log ORDER BY deliveredAt DESC LIMIT 200")
    suspend fun log(): List<NotifLogEntity>

    @Query("DELETE FROM notif_log WHERE id NOT IN (SELECT id FROM notif_log ORDER BY deliveredAt DESC LIMIT 300)")
    suspend fun trimLog()

    @Query("DELETE FROM notif_log")
    suspend fun clearLog()

    @Query("SELECT * FROM scheduled_alarms ORDER BY triggerAt")
    fun scheduledFlow(): Flow<List<ScheduledAlarmEntity>>

    @Query("SELECT * FROM scheduled_alarms ORDER BY triggerAt")
    suspend fun scheduled(): List<ScheduledAlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduled(items: List<ScheduledAlarmEntity>)

    @Query("DELETE FROM scheduled_alarms WHERE requestCode = :code")
    suspend fun deleteScheduled(code: Int)

    @Query("DELETE FROM scheduled_alarms WHERE requestCode < :maxCode")
    suspend fun deletePlanned(maxCode: Int)

    @Transaction
    suspend fun replacePlanned(maxCode: Int, items: List<ScheduledAlarmEntity>) {
        deletePlanned(maxCode)
        insertScheduled(items)
    }
}
