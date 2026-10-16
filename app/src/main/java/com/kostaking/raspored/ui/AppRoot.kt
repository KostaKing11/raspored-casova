package com.kostaking.raspored.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kostaking.raspored.alarm.PermissionSnapshot
import com.kostaking.raspored.alarm.PermissionStatus
import com.kostaking.raspored.core.SchoolDayPager
import com.kostaking.raspored.ui.components.SpaceBackground
import com.kostaking.raspored.ui.screens.CalendarScreen
import com.kostaking.raspored.ui.screens.DayScreen
import com.kostaking.raspored.ui.screens.DaysOffScreen
import com.kostaking.raspored.ui.screens.DebugScreen
import com.kostaking.raspored.ui.screens.EditScheduleScreen
import com.kostaking.raspored.ui.screens.EventEditorScreen
import com.kostaking.raspored.ui.screens.OnboardingScreen
import com.kostaking.raspored.ui.screens.PeriodsScreen
import com.kostaking.raspored.ui.screens.SettingsScreen
import com.kostaking.raspored.ui.screens.SubjectsScreen
import com.kostaking.raspored.ui.screens.UpcomingScreen
import com.kostaking.raspored.ui.theme.LocalExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Route {
    data object Main : Route
    data class EditSchedule(val day: Int, val lessonId: Long? = null) : Route
    data object Periods : Route
    data object Subjects : Route
    data object DaysOff : Route
    data object Debug : Route
    data class Onboarding(val firstRun: Boolean) : Route
    data class EventEditor(val eventId: Long?, val date: LocalDate, val period: Int?, val subjectId: Long?) : Route
}

@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(2000 - System.currentTimeMillis() % 1000)
            value = LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val scheduled by vm.scheduled.collectAsStateWithLifecycle()
    val now = rememberNow()
    val today = now.toLocalDate()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var permissions by remember { mutableStateOf<PermissionSnapshot?>(null) }
    fun refreshPermissions() {
        val old = permissions
        val new = PermissionStatus.snapshot(context)
        permissions = new
        if (old != null && (old.exactAlarms != new.exactAlarms)) vm.rescheduleNow()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshPermissions() }

    val stack = remember {
        mutableStateListOf<Route>(Route.Main).apply {
            if (!settings.onboardingDone) add(Route.Onboarding(firstRun = true))
        }
    }
    val navForward = remember { booleanArrayOf(true) }
    fun push(r: Route) {
        navForward[0] = true
        stack.add(r)
    }
    fun pop() {
        navForward[0] = false
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = SchoolDayPager.pageFor(LocalDate.now())) { SchoolDayPager.PAGE_COUNT }
    BackHandler(enabled = stack.size == 1 && tab != 0) { tab = 0 }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val s = state ?: return
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            if (navForward[0]) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Main -> SpaceBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = if (LocalExtraColors.current.isDark) Color(0xE6070914) else MaterialTheme.colorScheme.surface,
                        ) {
                            val items = listOf(
                                Triple("Raspored", Icons.Rounded.ViewDay, "tabDay"),
                                Triple("Kalendar", Icons.Rounded.CalendarMonth, "tabCalendar"),
                                Triple("Predstojeće", Icons.AutoMirrored.Rounded.EventNote, "tabUpcoming"),
                                Triple("Podešavanja", Icons.Rounded.Settings, "tabSettings"),
                            )
                            items.forEachIndexed { i, (label, icon, tag) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(tag),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Crossfade(targetState = tab, label = "tab") { t ->
                            when (t) {
                                0 -> DayScreen(
                                    state = s,
                                    pagerState = pagerState,
                                    now = now,
                                    permissions = permissions,
                                    onFixPermissions = { push(Route.Onboarding(firstRun = false)) },
                                    onAddEvent = { date, period, subject -> push(Route.EventEditor(null, date, period, subject)) },
                                    onEditEvent = { id -> push(Route.EventEditor(id, today, null, null)) },
                                    onEditLesson = { id ->
                                        val l = s.lessons.firstOrNull { it.id == id }
                                        push(Route.EditSchedule(l?.day ?: 1, id))
                                    },
                                )
                                1 -> CalendarScreen(
                                    state = s,
                                    today = today,
                                    onOpenDay = { d ->
                                        tab = 0
                                        scope.launch { pagerState.scrollToPage(SchoolDayPager.pageFor(d)) }
                                    },
                                    onAddEvent = { d -> push(Route.EventEditor(null, d, null, null)) },
                                    onEditEvent = { id -> push(Route.EventEditor(id, today, null, null)) },
                                )
                                2 -> UpcomingScreen(
                                    state = s,
                                    today = today,
                                    onAdd = { push(Route.EventEditor(null, SchoolDayPager.schoolDayOnOrAfter(today), null, null)) },
                                    onEdit = { id -> push(Route.EventEditor(id, today, null, null)) },
                                )
                                else -> SettingsScreen(
                                    settings = settings,
                                    onUpdate = { vm.updateSettings(it) },
                                    onEditSchedule = { push(Route.EditSchedule(SchoolDayPager.schoolDayOnOrAfter(today).dayOfWeek.value)) },
                                    onPeriods = { push(Route.Periods) },
                                    onSubjects = { push(Route.Subjects) },
                                    onDaysOff = { push(Route.DaysOff) },
                                    onPermissions = { push(Route.Onboarding(firstRun = false)) },
                                    onDebug = { push(Route.Debug) },
                                    onExport = { vm.exportTo(it) },
                                    onImport = { vm.importFrom(it) },
                                    onReset = { vm.resetToOriginal() },
                                )
                            }
                        }
                    }
                }
            }

            is Route.EditSchedule -> EditScheduleScreen(
                state = s,
                initialDay = route.day,
                initialLessonId = route.lessonId,
                onBack = ::pop,
                onOpenPeriods = { push(Route.Periods) },
                onOpenSubjects = { push(Route.Subjects) },
                onSaveLesson = { l, name -> vm.saveLesson(l, name) },
                onDeleteLesson = { vm.deleteLesson(it) },
                onMerge = { vm.mergeWithNext(it) },
                onSplit = { vm.splitBlock(it) },
            )

            Route.Periods -> PeriodsScreen(
                state = s,
                onBack = ::pop,
                onSave = { vm.savePeriod(it) },
                onAdd = { vm.addPeriod() },
                onRemoveLast = { vm.removeLastPeriod() },
            )

            Route.Subjects -> SubjectsScreen(
                state = s,
                onBack = ::pop,
                onSave = { vm.saveSubject(it) },
                onDelete = { vm.deleteSubject(it) },
            )

            Route.DaysOff -> DaysOffScreen(
                state = s,
                today = today,
                onBack = ::pop,
                onSave = { vm.saveDayOff(it) },
                onDelete = { vm.deleteDayOff(it) },
            )

            Route.Debug -> DebugScreen(
                state = s,
                settings = settings,
                permissions = permissions,
                scheduled = scheduled,
                log = log,
                nextSystemAlarm = vm.nextSystemAlarmClock(),
                onBack = ::pop,
                onSendTest = { vm.sendTest() },
                onSimulate = { d, i -> vm.simulate(d, i) },
                onReschedule = { vm.rescheduleNow() },
                onClearLog = { vm.clearLog() },
                onOpenPermissions = { push(Route.Onboarding(firstRun = false)) },
            )

            is Route.Onboarding -> OnboardingScreen(
                permissions = permissions,
                samsungDone = settings.samsungStepDone,
                firstRun = route.firstRun,
                onSamsungDone = { v -> vm.updateSettings { it.copy(samsungStepDone = v) } },
                onRefresh = { refreshPermissions() },
                onSendTest = { vm.sendTest() },
                onFinish = {
                    vm.updateSettings { it.copy(onboardingDone = true) }
                    refreshPermissions()
                    pop()
                },
            )

            is Route.EventEditor -> EventEditorScreen(
                state = s,
                eventId = route.eventId,
                initialDate = route.date,
                initialPeriod = route.period,
                initialSubjectId = route.subjectId,
                onSave = { vm.saveEvent(it) },
                onDelete = { vm.deleteEvent(it) },
                onBack = ::pop,
            )
        }
    }
    // Snackbars on secondary screens
    if (stack.last() != Route.Main) {
        Box(Modifier.fillMaxSize()) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}
