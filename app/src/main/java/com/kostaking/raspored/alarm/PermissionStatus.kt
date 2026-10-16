package com.kostaking.raspored.alarm

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    val backgroundRestricted: Boolean,
    val lessonsChannelEnabled: Boolean,
    val standbyBucket: String?,
    val deviceIdle: Boolean,
) {
    /** Everything needed for on-time notifications is in place. */
    val allGood: Boolean get() = notifications && exactAlarms && batteryUnrestricted && !backgroundRestricted && lessonsChannelEnabled

    val missing: List<String>
        get() = buildList {
            if (!notifications) add("dozvola za notifikacije")
            if (notifications && !lessonsChannelEnabled) add("kanal „Sledeći čas“ je isključen")
            if (!exactAlarms) add("tačni alarmi")
            if (!batteryUnrestricted) add("optimizacija baterije je uključena")
            if (backgroundRestricted) add("rad u pozadini je ograničen")
        }
}

object PermissionStatus {

    fun snapshot(context: Context): PermissionSnapshot {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm?.getNotificationChannel(NotificationHelper.CHANNEL_LESSONS)
        return PermissionSnapshot(
            notifications = notificationsGranted(context),
            exactAlarms = exactAlarms(context),
            batteryUnrestricted = ignoringBatteryOptimizations(context),
            backgroundRestricted = backgroundRestricted(context),
            lessonsChannelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
            standbyBucket = standbyBucket(context),
            deviceIdle = context.getSystemService(PowerManager::class.java)?.isDeviceIdleMode == true,
        )
    }

    fun notificationsGranted(context: Context): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtime && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun exactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun backgroundRestricted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    fun standbyBucket(context: Context): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val bucket = context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket ?: return null
        return when (bucket) {
            5 -> "EXEMPTED"
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            45 -> "RESTRICTED"
            else -> bucket.toString()
        }
    }

    // ---- Intents that fix things ----

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            appDetails(context)
        }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Tries to open Samsung "Battery / Background usage limits"; falls back to the app's info page. */
    fun openSamsungBatterySettings(context: Context) {
        val candidates = listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        )
        for (c in candidates) {
            try {
                context.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        safeStart(context, appDetails(context))
    }

    fun safeStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        try {
            context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
