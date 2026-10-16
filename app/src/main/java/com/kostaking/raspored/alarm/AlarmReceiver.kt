package com.kostaking.raspored.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kostaking.raspored.RasporedApp
import com.kostaking.raspored.core.NotificationKind
import com.kostaking.raspored.data.NotifLogEntity
import kotlinx.coroutines.launch

/** Receives our exact alarms, posts the notification immediately and re-plans. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RasporedApp).container
        val pending = goAsync()

        if (intent.action == ACTION_MAINTENANCE) {
            container.scope.launch {
                try {
                    container.scheduler.reschedule("maintenance")
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val deliveredAt = System.currentTimeMillis()
        val kind = NotificationKind.fromName(intent.getStringExtra(EXTRA_KIND))
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, deliveredAt)
        val code = intent.getIntExtra(EXTRA_CODE, -1)
        val hasEvent = intent.getBooleanExtra(EXTRA_HAS_EVENT, false)

        // Post first, everything else afterwards.
        val shown = NotificationHelper.show(context, kind, title, text, triggerAt, code, hasEvent)
        Log.i(
            AlarmScheduler.TAG,
            "delivered kind=$kind code=$code shown=$shown scheduled=$triggerAt actual=$deliveredAt delayMs=${deliveredAt - triggerAt} title=\"$title\"",
        )

        container.scope.launch {
            try {
                val dao = container.db.alarmDao()
                dao.insertLog(NotifLogEntity(kind = kind.name, title = title, scheduledAt = triggerAt, deliveredAt = deliveredAt))
                dao.trimLog()
                dao.deleteScheduled(code)
                if (kind != NotificationKind.TEST && kind != NotificationKind.SIMULATED) {
                    container.scheduler.reschedule("after-${kind.name}")
                }
            } catch (e: Exception) {
                Log.e(AlarmScheduler.TAG, "post-delivery work failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.kostaking.raspored.action.FIRE"
        const val ACTION_MAINTENANCE = "com.kostaking.raspored.action.MAINTENANCE"
        const val EXTRA_CODE = "code"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TRIGGER_AT = "triggerAt"
        const val EXTRA_HAS_EVENT = "hasEvent"
    }
}

/** Boot, app update, time / timezone change, exact-alarm permission granted → re-schedule everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RasporedApp).container
        val pending = goAsync()
        Log.i(AlarmScheduler.TAG, "system event ${intent.action}")
        container.scope.launch {
            try {
                container.scheduler.reschedule(intent.action ?: "system")
            } catch (e: Exception) {
                Log.e(AlarmScheduler.TAG, "reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
