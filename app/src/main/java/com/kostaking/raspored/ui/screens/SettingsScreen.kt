@file:OptIn(ExperimentalLayoutApi::class)

package com.kostaking.raspored.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.BuildConfig
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.data.AlarmMode
import com.kostaking.raspored.data.AppSettings
import com.kostaking.raspored.data.ThemeMode
import com.kostaking.raspored.ui.components.ConfirmDialog
import com.kostaking.raspored.ui.components.GlassCard
import com.kostaking.raspored.ui.components.SectionTitle
import com.kostaking.raspored.ui.components.TimePickerDialog
import com.kostaking.raspored.ui.theme.LocalExtraColors
import java.time.LocalDate

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onEditSchedule: () -> Unit,
    onPeriods: () -> Unit,
    onSubjects: () -> Unit,
    onDaysOff: () -> Unit,
    onPermissions: () -> Unit,
    onDebug: () -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onReset: () -> Unit,
) {
    var pickTime by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("settingsList"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
    ) {
        item {
            Text("Podešavanja", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        item { SectionTitle("Raspored") }
        item {
            GlassCard {
                NavRow(Icons.Rounded.EditCalendar, "Uredi raspored", "Predmeti, učionice, blokovi", onEditSchedule, "navEditSchedule")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.Schedule, "Termini časova", "Početak i kraj svakog časa", onPeriods, "navPeriods")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.Palette, "Predmeti i boje", "Boja svakog predmeta", onSubjects, "navSubjects")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.BeachAccess, "Neradni dani", "Praznici i raspust", onDaysOff, "navDaysOff")
            }
        }

        item { SectionTitle("Notifikacije o časovima") }
        item {
            GlassCard {
                SwitchRow("Sledeći čas", "Tačno kad se čas završi: sledeći predmet i učionica", settings.nextLesson, "swNext") { v ->
                    onUpdate { it.copy(nextLesson = v) }
                }
                SwitchRow("I između dva dela bloka", "Za dvočas stiže i posle prvog dela", settings.betweenBlockParts, "swBlock") { v ->
                    onUpdate { it.copy(betweenBlockParts = v) }
                }
                SwitchRow("Kraj nastave 🎉", "Posle poslednjeg časa, uz napomenu za sutrašnji kontrolni", settings.endOfDay, "swEod") { v ->
                    onUpdate { it.copy(endOfDay = v) }
                }
                SwitchRow("Podsetnik pre prvog časa", "${settings.beforeFirstMinutes} min pre početka nastave", settings.beforeFirst, "swFirst") { v ->
                    onUpdate { it.copy(beforeFirst = v) }
                }
                if (settings.beforeFirst) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(5, 10, 15, 20, 30, 45, 60).forEach { m ->
                            FilterChip(
                                selected = settings.beforeFirstMinutes == m,
                                onClick = { onUpdate { it.copy(beforeFirstMinutes = m) } },
                                label = { Text("$m min") },
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle("Podsetnici za kontrolne") }
        item {
            GlassCard {
                SwitchRow("Veče pre", "u ${Format.minutesOfDay(settings.eventEveningMinutes)} – tapni za promenu vremena", settings.eventEvening, "swEvening",
                    onClickText = { pickTime = "evening" }) { v -> onUpdate { it.copy(eventEvening = v) } }
                SwitchRow("Ujutru tog dana", "u ${Format.minutesOfDay(settings.eventMorningMinutes)} – tapni za promenu vremena", settings.eventMorning, "swMorning",
                    onClickText = { pickTime = "morning" }) { v -> onUpdate { it.copy(eventMorning = v) } }
            }
        }

        item { SectionTitle("Izgled") }
        item {
            GlassCard {
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ThemeMode.entries.forEach { t ->
                        FilterChip(
                            selected = settings.theme == t,
                            onClick = { onUpdate { it.copy(theme = t) } },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("theme-${t.name}"),
                        )
                    }
                }
            }
        }

        item { SectionTitle("Pouzdanost") }
        item {
            GlassCard {
                Text("Način zakazivanja alarma", style = MaterialTheme.typography.titleSmall)
                AlarmMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onUpdate { it.copy(alarmMode = m) } }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = settings.alarmMode == m, onClick = { onUpdate { it.copy(alarmMode = m) } })
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge)
                            Text(m.description, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted)
                        }
                    }
                }
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.Security, "Dozvole i baterija", "Notifikacije, tačni alarmi, Samsung podešavanja", onPermissions, "navPermissions")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.BugReport, "Debug / test", "Test notifikacije, zakazani alarmi, log kašnjenja", onDebug, "navDebug")
            }
        }

        item { SectionTitle("Backup") }
        item {
            GlassCard {
                NavRow(Icons.Rounded.Upload, "Izvezi u JSON", "Raspored, predmeti, kontrolni i neradni dani", {
                    exportLauncher.launch("raspored-backup-${LocalDate.now()}.json")
                }, "navExport")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.Download, "Uvezi iz JSON-a", "Zamenjuje trenutne podatke", {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }, "navImport")
                HorizontalDivider(color = LocalExtraColors.current.divider)
                NavRow(Icons.Rounded.Restore, "Vrati originalni raspored", "Termini, časovi i boje kao na početku (kontrolni ostaju)", {
                    confirmReset = true
                }, "navReset")
            }
        }
        item {
            Text(
                "Raspored časova ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.textMuted,
                modifier = Modifier.padding(top = 20.dp, start = 4.dp),
            )
        }
    }

    pickTime?.let { which ->
        val initial = if (which == "evening") settings.eventEveningMinutes else settings.eventMorningMinutes
        TimePickerDialog(
            title = if (which == "evening") "Podsetnik veče pre" else "Podsetnik ujutru",
            initialMinutes = initial,
            onDismiss = { pickTime = null },
            onConfirm = { m ->
                onUpdate { if (which == "evening") it.copy(eventEveningMinutes = m) else it.copy(eventMorningMinutes = m) }
                pickTime = null
            },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Vratiti originalni raspored?",
            text = "Svi časovi i termini biće vraćeni na početne. Kontrolni i neradni dani ostaju.",
            confirm = "Vrati",
            onDismiss = { confirmReset = false },
            onConfirm = onReset,
        )
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            title = "Uvesti backup?",
            text = "Trenutni raspored, predmeti, kontrolni i neradni dani biće zamenjeni podacima iz fajla.",
            confirm = "Uvezi",
            onDismiss = { confirmImport = null },
            onConfirm = { onImport(uri) },
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = LocalExtraColors.current.textMuted)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onClickText: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .then(if (onClickText != null) Modifier.clickable(onClick = onClickText) else Modifier),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
