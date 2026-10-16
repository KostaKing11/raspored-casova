package com.kostaking.raspored.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.alarm.PermissionSnapshot
import com.kostaking.raspored.core.Format
import com.kostaking.raspored.core.NotificationKind
import com.kostaking.raspored.data.AppSettings
import com.kostaking.raspored.data.NotifLogEntity
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.data.ScheduledAlarmEntity
import com.kostaking.raspored.ui.components.DropdownField
import com.kostaking.raspored.ui.components.GlassCard
import com.kostaking.raspored.ui.components.SectionTitle
import com.kostaking.raspored.ui.components.SubScreen
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.NowRed
import com.kostaking.raspored.ui.theme.OkGreen
import com.kostaking.raspored.ui.theme.WarnAmber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

@Composable
fun DebugScreen(
    state: ScheduleState,
    settings: AppSettings,
    permissions: PermissionSnapshot?,
    scheduled: List<ScheduledAlarmEntity>,
    log: List<NotifLogEntity>,
    nextSystemAlarm: Long?,
    onBack: () -> Unit,
    onSendTest: () -> Unit,
    onSimulate: (LocalDate, Int) -> Unit,
    onReschedule: () -> Unit,
    onClearLog: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceIn(1, 5)) }
    var blockIndex by remember { mutableIntStateOf(0) }
    var showAllAlarms by remember { mutableStateOf(false) }
    val blocks = state.timetable.blocksFor(DayOfWeek.of(day))
    val zone = ZoneId.systemDefault()

    SubScreen(title = "Debug / test", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("debugList"),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 48.dp),
        ) {
            item { SectionTitle("Status") }
            item {
                GlassCard {
                    if (permissions != null) {
                        StatusRow("Notifikacije dozvoljene", permissions.notifications)
                        StatusRow("Kanal „Sledeći čas“ uključen", permissions.lessonsChannelEnabled)
                        StatusRow("Tačni alarmi (exact alarm)", permissions.exactAlarms)
                        StatusRow("Optimizacija baterije isključena", permissions.batteryUnrestricted)
                        StatusRow("Rad u pozadini nije ograničen", !permissions.backgroundRestricted)
                        StatusRow("Samsung „nikad ne spava“ (potvrđeno ručno)", settings.samsungStepDone)
                        InfoLine("App standby bucket", permissions.standbyBucket ?: "—")
                        InfoLine("Uređaj trenutno u Doze režimu", if (permissions.deviceIdle) "da" else "ne")
                    }
                    InfoLine("Način alarma", settings.alarmMode.label)
                    InfoLine(
                        "Sledeći sistemski budilnik",
                        nextSystemAlarm?.let { formatMillis(it, zone) } ?: "nema",
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenPermissions) { Text("Otvori podešavanje dozvola") }
                }
            }

            item { SectionTitle("Testovi") }
            item {
                GlassCard {
                    Button(onClick = onSendTest, modifier = Modifier.fillMaxWidth().testTag("sendTest")) {
                        Text("Pošalji test notifikaciju za 1 min")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Simuliraj kraj časa", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Za 1 min stiže prava notifikacija „Sledeći čas…“ kao da se izabrani čas upravo završio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalExtraColors.current.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownField(
                            label = "Dan",
                            value = Format.dayName(DayOfWeek.of(day)),
                            options = (1..5).toList(),
                            optionLabel = { Format.dayName(DayOfWeek.of(it)) },
                            onSelect = { day = it; blockIndex = 0 },
                            modifier = Modifier.weight(1f),
                        )
                        DropdownField(
                            label = "Čas",
                            value = blocks.getOrNull(blockIndex)?.let { "${it.numberLabel}. ${it.subjectName}" } ?: "—",
                            options = blocks.indices.toList(),
                            optionLabel = { "${blocks[it].numberLabel}. ${blocks[it].subjectName}" },
                            onSelect = { blockIndex = it },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val date = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)))
                            onSimulate(date, blockIndex)
                        },
                        enabled = blocks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("simulate"),
                    ) { Text("Simuliraj kraj časa (za 1 min)") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReschedule, modifier = Modifier.fillMaxWidth().testTag("reschedule")) {
                        Text("Ponovo zakaži sve alarme")
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Zakazani alarmi (${scheduled.size})", Modifier.weight(1f))
                    if (scheduled.size > 12) {
                        TextButton(onClick = { showAllAlarms = !showAllAlarms }) { Text(if (showAllAlarms) "Manje" else "Svi") }
                    }
                }
            }
            val shown = if (showAllAlarms) scheduled else scheduled.take(12)
            item {
                GlassCard {
                    if (scheduled.isEmpty()) Text("Nema zakazanih alarma", color = LocalExtraColors.current.textMuted)
                    shown.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = LocalExtraColors.current.divider)
                        Column(Modifier.padding(vertical = 6.dp).testTag("alarm-$i")) {
                            Row {
                                Text(formatMillis(a.triggerAt, zone), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    NotificationKind.fromName(a.kind).label + if (!a.exact) " · NETAČAN" else "",
                                    color = if (a.exact) MaterialTheme.colorScheme.primary else NowRed,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Text(a.title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Log isporuke (${log.size})", Modifier.weight(1f))
                    TextButton(onClick = onClearLog) { Text("Obriši") }
                }
            }
            item {
                GlassCard {
                    if (log.isEmpty()) Text("Još nijedna notifikacija nije isporučena.", color = LocalExtraColors.current.textMuted)
                    else {
                        val delays = log.map { it.deliveredAt - it.scheduledAt }
                        Text(
                            "Prosečno kašnjenje: ${formatDelay(delays.average().toLong())} · najveće: ${formatDelay(delays.max())}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            items(log, key = { it.id }) { e ->
                val delay = e.deliveredAt - e.scheduledAt
                val color = when {
                    abs(delay) < 5_000 -> OkGreen
                    abs(delay) < 60_000 -> WarnAmber
                    else -> NowRed
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(formatDelay(delay), color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        "zakazano ${formatMillis(e.scheduledAt, zone)} · stiglo ${formatMillis(e.deliveredAt, zone, millis = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalExtraColors.current.textMuted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                HorizontalDivider(color = LocalExtraColors.current.divider)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            null,
            tint = if (ok) OkGreen else NowRed,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.Unspecified)
    }
}

fun formatMillis(ms: Long, zone: ZoneId, millis: Boolean = false): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val base = "${Format.dayShort(t.dayOfWeek)} %02d.%02d. %02d:%02d:%02d".format(t.dayOfMonth, t.monthValue, t.hour, t.minute, t.second)
    return if (millis) base + ".%03d".format(t.nano / 1_000_000) else base
}

fun formatDelay(ms: Long): String {
    val sign = if (ms < 0) "−" else "+"
    val a = abs(ms)
    return when {
        a < 60_000 -> "$sign%.1f s".format(a / 1000.0)
        else -> "$sign${a / 60_000} min ${(a % 60_000) / 1000} s"
    }
}
