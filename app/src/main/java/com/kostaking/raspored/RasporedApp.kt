package com.kostaking.raspored

import android.app.Application
import android.content.Context
import android.util.Log
import com.kostaking.raspored.alarm.AlarmScheduler
import com.kostaking.raspored.alarm.NotificationHelper
import com.kostaking.raspored.data.AppDatabase
import com.kostaking.raspored.data.ScheduleRepository
import com.kostaking.raspored.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AppContainer(context: Context, inMemoryDb: Boolean = false) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = AppDatabase.create(context, inMemoryDb)
    val repo = ScheduleRepository(db)
    val settings = SettingsRepository(context)
    val scheduler = AlarmScheduler(context, repo, settings, db.alarmDao())

    /** Seeds the original timetable and re-schedules alarms on every change of data or settings. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            try {
                repo.seedIfEmpty()
                scheduler.reschedule("app-start")
            } catch (e: Exception) {
                Log.e(AlarmScheduler.TAG, "startup failed", e)
            }
            combine(repo.state, settings.flow) { s, st -> s to st }
                .drop(1)
                .debounce(300)
                .collect {
                    try {
                        scheduler.reschedule("data-changed")
                    } catch (e: Exception) {
                        Log.e(AlarmScheduler.TAG, "reschedule failed", e)
                    }
                }
        }
    }
}

/** Implemented by the Application so components (and tests) can reach the [AppContainer]. */
interface ContainerHolder {
    val container: AppContainer
}

class RasporedApp : Application(), ContainerHolder {
    override lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        container = AppContainer(this)
        container.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as ContainerHolder).container
