package com.kostaking.raspored.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kostaking.raspored.container
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.data.AppSettings
import com.kostaking.raspored.data.DayOffEntity
import com.kostaking.raspored.data.EventEntity
import com.kostaking.raspored.data.LessonEntity
import com.kostaking.raspored.data.PeriodEntity
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.data.SubjectEntity
import com.kostaking.raspored.data.backup.Backup
import com.kostaking.raspored.data.backup.BackupException
import com.kostaking.raspored.ui.components.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val state: StateFlow<ScheduleState?> = c.repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<AppSettings> = c.settings.flow
    val log = c.db.alarmDao().logFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val scheduled = c.db.alarmDao().scheduledFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = c.settings.update(transform)

    // ---- Lessons ----

    fun saveLesson(lesson: LessonEntity, newSubjectName: String?) = viewModelScope.launch {
        val subjectId = if (!newSubjectName.isNullOrBlank()) {
            c.repo.subjectByNameOrCreate(newSubjectName, nextColor())
        } else lesson.subjectId
        c.repo.saveLesson(lesson.copy(subjectId = subjectId))
        toast("Čas je sačuvan")
    }

    fun deleteLesson(id: Long) = viewModelScope.launch { c.repo.deleteLesson(id); toast("Čas je obrisan") }

    fun mergeWithNext(id: Long) = viewModelScope.launch {
        if (c.repo.mergeWithNext(id)) toast("Spojeno u blok") else toast("Nema sledećeg časa za spajanje")
    }

    fun splitBlock(id: Long) = viewModelScope.launch { c.repo.splitBlock(id); toast("Blok je razdvojen") }

    // ---- Subjects ----

    private suspend fun nextColor(): Int {
        val used = c.repo.snapshot().subjects.map { it.color }.toSet()
        return Palette.colors.map { it.toInt() }.firstOrNull { it !in used } ?: Palette.colors.random().toInt()
    }

    fun saveSubject(subject: SubjectEntity) = viewModelScope.launch {
        val name = subject.name.trim()
        if (name.isEmpty()) {
            toast("Unesi naziv predmeta"); return@launch
        }
        val existing = c.repo.snapshot().subjects.firstOrNull { it.name.equals(name, true) && it.id != subject.id }
        if (existing != null) {
            toast("Predmet „$name“ već postoji"); return@launch
        }
        c.repo.saveSubject(subject.copy(name = name))
    }

    fun deleteSubject(id: Long) = viewModelScope.launch {
        if (!c.repo.deleteSubject(id)) toast("Predmet se koristi u rasporedu ili kontrolnom – prvo njih izmeni")
    }

    // ---- Periods ----

    fun savePeriod(p: PeriodEntity) = viewModelScope.launch {
        if (p.endMin <= p.startMin) {
            toast("Kraj časa mora biti posle početka"); return@launch
        }
        c.repo.savePeriod(p)
    }

    fun addPeriod() = viewModelScope.launch { c.repo.addPeriod() }
    fun removeLastPeriod() = viewModelScope.launch { c.repo.removeLastPeriod() }

    // ---- Events ----

    fun saveEvent(e: EventEntity) = viewModelScope.launch {
        c.repo.saveEvent(e)
        toast("Sačuvano: ${com.kostaking.raspored.core.EventType.fromName(e.type).label}, ${Format.shortDate(LocalDate.ofEpochDay(e.date))}")
    }

    fun deleteEvent(id: Long) = viewModelScope.launch { c.repo.deleteEvent(id); toast("Obrisano") }

    // ---- Days off ----

    fun saveDayOff(d: DayOffEntity) = viewModelScope.launch {
        if (d.endDate < d.startDate) {
            toast("Kraj perioda je pre početka"); return@launch
        }
        c.repo.saveDayOff(d)
        toast("Neradni dani su sačuvani")
    }

    fun deleteDayOff(id: Long) = viewModelScope.launch { c.repo.deleteDayOff(id) }

    // ---- Backup ----

    fun resetToOriginal() = viewModelScope.launch {
        c.repo.resetToOriginal()
        toast("Vraćen je originalni raspored")
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        try {
            val text = Backup.export(c.repo.snapshot())
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }
            toast("Backup je sačuvan")
        } catch (e: Exception) {
            toast("Izvoz nije uspeo: ${e.message}")
        }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }
            Backup.import(c.repo, text)
            toast("Backup je uvezen")
        } catch (e: BackupException) {
            toast(e.message ?: "Neispravan backup")
        } catch (e: Exception) {
            toast("Uvoz nije uspeo: ${e.message}")
        }
    }

    // ---- Debug ----

    fun sendTest() = viewModelScope.launch {
        val at = c.scheduler.scheduleTest(60)
        toast("Test notifikacija stiže u ${clock(at)}")
    }

    fun simulate(date: LocalDate, blockIndex: Int) = viewModelScope.launch {
        val at = c.scheduler.scheduleSimulated(date, blockIndex, 60)
        toast(if (at == null) "Taj čas ne postoji" else "Simulirana notifikacija stiže u ${clock(at)}")
    }

    fun rescheduleNow() = viewModelScope.launch {
        val r = c.scheduler.reschedule("manual")
        toast("Zakazano ${r.count} alarma" + if (!r.exact) " (NISU tačni – fali dozvola!)" else "")
    }

    fun clearLog() = viewModelScope.launch { c.db.alarmDao().clearLog() }

    fun canScheduleExact() = c.scheduler.canScheduleExact()
    fun nextSystemAlarmClock() = c.scheduler.nextSystemAlarmClock()

    private fun clock(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
    }
}
