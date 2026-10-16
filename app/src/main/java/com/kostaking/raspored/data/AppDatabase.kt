package com.kostaking.raspored.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        PeriodEntity::class,
        LessonEntity::class,
        EventEntity::class,
        DayOffEntity::class,
        NotifLogEntity::class,
        ScheduledAlarmEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        fun create(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "raspored.db")
            }
            return builder.build()
        }
    }
}
