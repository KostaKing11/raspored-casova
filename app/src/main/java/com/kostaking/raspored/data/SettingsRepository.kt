package com.kostaking.raspored.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.kostaking.raspored.core.PlannerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

enum class ThemeMode(val label: String) { DARK("Tamna"), LIGHT("Svetla"), SYSTEM("Sistemska") }

enum class AlarmMode(val label: String, val description: String) {
    ALARM_CLOCK(
        "AlarmClock (preporučeno)",
        "setAlarmClock – sistem ga tretira kao budilnik, ne odlaže ga ni u Doze režimu. U statusnoj traci se vidi ikonica budilnika.",
    ),
    EXACT_IDLE(
        "Exact + AllowWhileIdle",
        "setExactAndAllowWhileIdle – bez ikonice budilnika, ali u dubokom Doze režimu sistem može da ograniči učestalost.",
    ),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val nextLesson: Boolean = true,
    val betweenBlockParts: Boolean = false,
    val endOfDay: Boolean = true,
    val beforeFirst: Boolean = false,
    val beforeFirstMinutes: Int = 10,
    val eventEvening: Boolean = true,
    val eventEveningMinutes: Int = 19 * 60,
    val eventMorning: Boolean = true,
    val eventMorningMinutes: Int = 7 * 60,
    val alarmMode: AlarmMode = AlarmMode.ALARM_CLOCK,
    val onboardingDone: Boolean = false,
    val samsungStepDone: Boolean = false,
) {
    fun planner() = PlannerSettings(
        nextLessonEnabled = nextLesson,
        betweenBlockParts = betweenBlockParts,
        endOfDayEnabled = endOfDay,
        beforeFirstEnabled = beforeFirst,
        beforeFirstMinutes = beforeFirstMinutes,
        eventEveningEnabled = eventEvening,
        eventEveningTime = LocalTime.of(eventEveningMinutes / 60, eventEveningMinutes % 60),
        eventMorningEnabled = eventMorning,
        eventMorningTime = LocalTime.of(eventMorningMinutes / 60, eventMorningMinutes % 60),
    )
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val current: AppSettings get() = _flow.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_flow.value)
        write(next)
        _flow.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            nextLesson = prefs.getBoolean("nextLesson", d.nextLesson),
            betweenBlockParts = prefs.getBoolean("betweenBlockParts", d.betweenBlockParts),
            endOfDay = prefs.getBoolean("endOfDay", d.endOfDay),
            beforeFirst = prefs.getBoolean("beforeFirst", d.beforeFirst),
            beforeFirstMinutes = prefs.getInt("beforeFirstMinutes", d.beforeFirstMinutes),
            eventEvening = prefs.getBoolean("eventEvening", d.eventEvening),
            eventEveningMinutes = prefs.getInt("eventEveningMinutes", d.eventEveningMinutes),
            eventMorning = prefs.getBoolean("eventMorning", d.eventMorning),
            eventMorningMinutes = prefs.getInt("eventMorningMinutes", d.eventMorningMinutes),
            alarmMode = enumOr(prefs.getString("alarmMode", null), d.alarmMode),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
            samsungStepDone = prefs.getBoolean("samsungStepDone", d.samsungStepDone),
        )
    }

    private fun write(s: AppSettings) = prefs.edit(commit = true) {
        putString("theme", s.theme.name)
        putBoolean("nextLesson", s.nextLesson)
        putBoolean("betweenBlockParts", s.betweenBlockParts)
        putBoolean("endOfDay", s.endOfDay)
        putBoolean("beforeFirst", s.beforeFirst)
        putInt("beforeFirstMinutes", s.beforeFirstMinutes)
        putBoolean("eventEvening", s.eventEvening)
        putInt("eventEveningMinutes", s.eventEveningMinutes)
        putBoolean("eventMorning", s.eventMorning)
        putInt("eventMorningMinutes", s.eventMorningMinutes)
        putString("alarmMode", s.alarmMode.name)
        putBoolean("onboardingDone", s.onboardingDone)
        putBoolean("samsungStepDone", s.samsungStepDone)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
