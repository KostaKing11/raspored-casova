package com.kostaking.raspored.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kostaking.raspored.container
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.data.EventEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * adb shell am broadcast -a com.kostaking.raspored.debug.TEST_ALARM --el seconds 60 -p com.kostaking.raspored.debug
 * adb shell am broadcast -a com.kostaking.raspored.debug.DUMP -p com.kostaking.raspored.debug
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        val pending = goAsync()
        c.scope.launch {
            try {
                when (intent.action?.substringAfterLast('.')) {
                    "TEST_ALARM" -> {
                        val at = c.scheduler.scheduleTest(intent.getLongExtra("seconds", 60))
                        Log.i(TAG, "test alarm scheduled at=$at")
                    }
                    "SKIP_ONBOARDING" -> {
                        c.settings.update { it.copy(onboardingDone = true, samsungStepDone = true) }
                        Log.i(TAG, "onboarding skipped")
                    }
                    "SET_MODE" -> {
                        val mode = com.kostaking.raspored.data.AlarmMode.valueOf(intent.getStringExtra("mode") ?: "ALARM_CLOCK")
                        c.settings.update { it.copy(alarmMode = mode) }
                        val r = c.scheduler.reschedule("mode")
                        Log.i(TAG, "mode=$mode rescheduled count=${r.count}")
                    }
                    "RESCHEDULE" -> {
                        val r = c.scheduler.reschedule("debug")
                        Log.i(TAG, "rescheduled count=${r.count} exact=${r.exact}")
                    }
                    "ADD_EVENT" -> {
                        val subject = c.repo.snapshot().subjects.first { it.name == intent.getStringExtra("subject") }
                        val date = LocalDate.now().plusDays(intent.getLongExtra("days", 0))
                        c.repo.saveEvent(
                            EventEntity(
                                type = EventType.KONTROLNI.name,
                                subjectId = subject.id,
                                date = date.toEpochDay(),
                                periodIndex = intent.getIntExtra("period", -1).takeIf { it >= 0 },
                                note = intent.getStringExtra("note").orEmpty(),
                            ),
                        )
                        Log.i(TAG, "event added for $date")
                    }
                    "DUMP" -> {
                        val dao = c.db.alarmDao()
                        val scheduled = dao.scheduled()
                        Log.i(TAG, "DUMP scheduled=${scheduled.size} exact=${c.scheduler.canScheduleExact()}")
                        scheduled.forEach { Log.i(TAG, "SCHEDULED code=${it.requestCode} at=${it.triggerAt} kind=${it.kind} exact=${it.exact} title=\"${it.title}\"") }
                        dao.log().forEach { Log.i(TAG, "LOG kind=${it.kind} scheduled=${it.scheduledAt} delivered=${it.deliveredAt} delayMs=${it.deliveredAt - it.scheduledAt} title=\"${it.title}\"") }
                        Log.i(TAG, "DUMP end")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "command failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "RasporedDebug"
    }
}
