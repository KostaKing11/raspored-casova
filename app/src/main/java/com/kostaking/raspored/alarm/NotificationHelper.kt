package com.kostaking.raspored.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kostaking.raspored.MainActivity
import com.kostaking.raspored.R
import com.kostaking.raspored.core.NotificationKind

object NotificationHelper {
    const val CHANNEL_LESSONS = "lessons"
    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_TEST = "test"

    private const val ID_LESSON = 1
    private const val ID_TEST = 3
    private const val ID_SIMULATED = 4
    private const val ID_EVENT_BASE = 10_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun channel(id: String, name: String, description: String) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
                enableVibration(true)
                enableLights(true)
                setSound(sound, audio)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannels(
            listOf(
                channel(CHANNEL_LESSONS, "Sledeći čas", "Obaveštenje tačno kada se čas završi: koji je sledeći čas i u kojoj učionici."),
                channel(CHANNEL_EVENTS, "Kontrolni i testovi", "Podsetnici za kontrolne, testove i domaće."),
                channel(CHANNEL_TEST, "Test notifikacije", "Probne notifikacije iz debug ekrana."),
            ),
        )
    }

    fun channelFor(kind: NotificationKind): String = when (kind) {
        NotificationKind.EVENT_EVENING, NotificationKind.EVENT_MORNING -> CHANNEL_EVENTS
        NotificationKind.TEST -> CHANNEL_TEST
        else -> CHANNEL_LESSONS
    }

    fun idFor(kind: NotificationKind, requestCode: Int): Int = when (kind) {
        NotificationKind.EVENT_EVENING, NotificationKind.EVENT_MORNING -> ID_EVENT_BASE + requestCode
        NotificationKind.TEST -> ID_TEST
        NotificationKind.SIMULATED -> ID_SIMULATED
        // All lesson notifications share one id: the newest replaces the previous one.
        else -> ID_LESSON
    }

    /** Posts the notification. Returns false when notifications are blocked. */
    fun show(
        context: Context,
        kind: NotificationKind,
        title: String,
        text: String,
        whenMillis: Long,
        requestCode: Int,
        hasEvent: Boolean,
    ): Boolean {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val builder = NotificationCompat.Builder(context, channelFor(kind))
            .setSmallIcon(R.drawable.ic_stat_school)
            .setColor(
                if (hasEvent) 0xFFFF3B4F.toInt() else ContextCompat.getColor(context, R.color.notification_accent),
            )
            .setContentTitle(title)
            .setContentText(firstLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (kind.name.startsWith("EVENT")) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        return try {
            nm.notify(idFor(kind, requestCode), builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
