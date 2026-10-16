package com.kostaking.raspored.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB colour. */
    val color: Int,
)

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey val idx: Int,
    /** Minutes since midnight. */
    val startMin: Int,
    val endMin: Int,
)

@Entity(tableName = "lessons", indices = [Index("day"), Index("subjectId")])
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO day of week, 1 = Monday … 5 = Friday. */
    val day: Int,
    val startPeriod: Int,
    /** Number of consecutive periods; 2 = blok (dvočas). */
    val span: Int = 1,
    val subjectId: Long,
    val room: String,
)

@Entity(tableName = "events", indices = [Index("date")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val subjectId: Long,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val periodIndex: Int? = null,
    val note: String = "",
    val reminders: Boolean = true,
)

@Entity(tableName = "days_off")
data class DayOffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: Long,
    val endDate: Long,
    val label: String,
)

/** Every delivered notification: scheduled time vs. actual delivery time. */
@Entity(tableName = "notif_log")
data class NotifLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val scheduledAt: Long,
    val deliveredAt: Long,
)

/** Mirror of what is currently registered in AlarmManager (for cancelling and the debug screen). */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarmEntity(
    @PrimaryKey val requestCode: Int,
    val triggerAt: Long,
    val kind: String,
    val title: String,
    val text: String,
    val exact: Boolean,
)
