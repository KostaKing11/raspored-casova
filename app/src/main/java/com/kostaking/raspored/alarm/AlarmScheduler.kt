package com.kostaking.raspored.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kostaking.raspored.MainActivity
import com.kostaking.raspored.core.NotificationKind
import com.kostaking.raspored.core.NotificationPlanner
import com.kostaking.raspored.core.PlannedNotification
import com.kostaking.raspored.data.AlarmDao
import com.kostaking.raspored.data.AlarmMode
import com.kostaking.raspored.data.ScheduleRepository
import com.kostaking.raspored.data.ScheduledAlarmEntity
import com.kostaking.raspored.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns the planner output into exact AlarmManager alarms.
 *
 * Every change (data, settings, boot, time change, alarm fired) calls [reschedule], which cancels
 * everything previously registered and registers the next [NotificationPlanner.DEFAULT_HORIZON_DAYS]
 * days again. A daily maintenance alarm keeps the window rolling even when nothing fires
 * (e.g. during school holidays).
 */
class AlarmScheduler(
    private val context: Context,
    private val repo: ScheduleRepository,
    private val settings: SettingsRepository,
    private val alarmDao: AlarmDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val am: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    data class Result(val count: Int, val exact: Boolean)

    suspend fun reschedule(reason: String): Result = mutex.withLock {
        val state = repo.snapshot()
        val now = clock()
        val zone = ZoneId.systemDefault()
        val plan = NotificationPlanner.plan(state.timetable, settings.current.planner(), now)
            .take(MAX_PLANNED)
        val exact = canScheduleExact()

        // Cancel everything we registered before.
        alarmDao.scheduled().filter { it.requestCode < FIRST_ONE_OFF_CODE }.forEach { cancel(it.requestCode) }

        val entries = plan.mapIndexed { i, p ->
            ScheduledAlarmEntity(
                requestCode = FIRST_PLAN_CODE + i,
                triggerAt = p.at.atZone(zone).toInstant().toEpochMilli(),
                kind = p.kind.name,
                title = p.title,
                text = p.text,
                exact = exact,
            )
        }
        entries.forEachIndexed { i, e -> set(e, plan[i].hasEvent) }
        alarmDao.replacePlanned(FIRST_ONE_OFF_CODE, entries)
        scheduleMaintenance(now, zone)
        Log.i(TAG, "rescheduled ${entries.size} alarms (reason=$reason, exact=$exact, mode=${settings.current.alarmMode})")
        Result(entries.size, exact)
    }

    /** "Pošalji test notifikaciju za 1 min" */
    suspend fun scheduleTest(delaySeconds: Long = 60): Long {
        val at = System.currentTimeMillis() + delaySeconds * 1000
        val time = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
        scheduleOneOff(
            code = CODE_TEST,
            triggerAt = at,
            kind = NotificationKind.TEST,
            title = "Test notifikacija ✅",
            text = "Zakazana za ${"%02d:%02d:%02d".format(time.hour, time.minute, time.second)}. Pogledaj kašnjenje u debug ekranu.",
            hasEvent = false,
        )
        return at
    }

    /** "Simuliraj kraj časa": the real notification for [blockIndex] on [date], delivered in 1 minute. */
    suspend fun scheduleSimulated(date: LocalDate, blockIndex: Int, delaySeconds: Long = 60): Long? {
        val state = repo.snapshot()
        val n: PlannedNotification = NotificationPlanner.afterBlock(state.timetable, date, blockIndex) ?: return null
        val at = System.currentTimeMillis() + delaySeconds * 1000
        scheduleOneOff(CODE_SIMULATED, at, NotificationKind.SIMULATED, n.title, n.text, n.hasEvent)
        return at
    }

    private suspend fun scheduleOneOff(
        code: Int,
        triggerAt: Long,
        kind: NotificationKind,
        title: String,
        text: String,
        hasEvent: Boolean,
    ) {
        val e = ScheduledAlarmEntity(code, triggerAt, kind.name, title, text, canScheduleExact())
        cancel(code)
        set(e, hasEvent)
        alarmDao.insertScheduled(listOf(e))
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    /** Trigger time of the next alarm-clock alarm known to the system (any app). */
    fun nextSystemAlarmClock(): Long? = am.nextAlarmClock?.triggerTime

    private fun set(e: ScheduledAlarmEntity, hasEvent: Boolean) {
        val pi = pendingIntent(e.requestCode, fireIntent(e, hasEvent), PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (!e.exact) {
                // No exact-alarm permission: best effort, may be late.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.triggerAt, pi)
            } else when (settings.current.alarmMode) {
                AlarmMode.ALARM_CLOCK -> am.setAlarmClock(AlarmManager.AlarmClockInfo(e.triggerAt, showIntent()), pi)
                AlarmMode.EXACT_IDLE -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.triggerAt, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "exact alarm refused, falling back to inexact", se)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.triggerAt, pi)
        }
    }

    private fun scheduleMaintenance(now: LocalDateTime, zone: ZoneId) {
        val at = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 5)).atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_MAINTENANCE)
            .setData(Uri.parse("raspored://alarm/$CODE_MAINTENANCE"))
        val pi = pendingIntent(CODE_MAINTENANCE, intent, PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(code: Int) {
        val pi = pendingIntent(code, baseIntent(code), PendingIntent.FLAG_NO_CREATE) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun baseIntent(code: Int) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_FIRE)
        .setData(Uri.parse("raspored://alarm/$code"))

    private fun fireIntent(e: ScheduledAlarmEntity, hasEvent: Boolean) = baseIntent(e.requestCode)
        .putExtra(AlarmReceiver.EXTRA_CODE, e.requestCode)
        .putExtra(AlarmReceiver.EXTRA_KIND, e.kind)
        .putExtra(AlarmReceiver.EXTRA_TITLE, e.title)
        .putExtra(AlarmReceiver.EXTRA_TEXT, e.text)
        .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, e.triggerAt)
        .putExtra(AlarmReceiver.EXTRA_HAS_EVENT, hasEvent)

    private fun pendingIntent(code: Int, intent: Intent, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, code, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val TAG = "RasporedAlarm"
        const val FIRST_PLAN_CODE = 1000
        const val MAX_PLANNED = 400
        const val FIRST_ONE_OFF_CODE = 900_000
        const val CODE_MAINTENANCE = 900_000
        const val CODE_TEST = 900_001
        const val CODE_SIMULATED = 900_002
    }
}
