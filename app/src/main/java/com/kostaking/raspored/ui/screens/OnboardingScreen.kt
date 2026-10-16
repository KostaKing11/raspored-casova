package com.kostaking.raspored.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.alarm.PermissionSnapshot
import com.kostaking.raspored.alarm.PermissionStatus
import com.kostaking.raspored.ui.components.GlassCard
import com.kostaking.raspored.ui.components.SpaceBackground
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.OkGreen

/**
 * First-run setup (also reachable from Settings → Dozvole i baterija): walks through every
 * permission / battery setting needed for on-time notifications on Samsung phones.
 */
@Composable
fun OnboardingScreen(
    permissions: PermissionSnapshot?,
    samsungDone: Boolean,
    firstRun: Boolean,
    onSamsungDone: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSendTest: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onRefresh()
        if (!granted) PermissionStatus.safeStart(context, PermissionStatus.appNotificationSettings(context))
    }
    val p = permissions

    SpaceBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("onboarding"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (firstRun) "Dobro došao! 👋" else "Dozvole i baterija",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Da bi notifikacija „Sledeći čas“ stizala tačno u minut kada se čas završi, potrebno je par podešavanja. " +
                    "Samsung telefoni agresivno uspavljuju aplikacije, zato su koraci 3 i 4 važni.",
                color = LocalExtraColors.current.textMuted,
            )

            Step(
                n = 1,
                icon = Icons.Rounded.Notifications,
                title = "Notifikacije",
                text = "Dozvoli aplikaciji da šalje obaveštenja.",
                done = p?.notifications == true && p.lessonsChannelEnabled,
            ) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && p?.notifications != true) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionStatus.safeStart(context, PermissionStatus.appNotificationSettings(context))
                        }
                    },
                    modifier = Modifier.testTag("grantNotifications"),
                ) { Text("Dozvoli notifikacije") }
            }

            Step(
                n = 2,
                icon = Icons.Rounded.Alarm,
                title = "Tačni alarmi",
                text = "Omogućava da obaveštenje stigne tačno u sekund (npr. 09:15:00). Na Androidu 13+ je uključeno automatski.",
                done = p?.exactAlarms == true,
            ) {
                FilledTonalButton(onClick = { PermissionStatus.safeStart(context, PermissionStatus.exactAlarmSettings(context)) }) {
                    Text("Otvori podešavanje")
                }
            }

            Step(
                n = 3,
                icon = Icons.Rounded.BatteryChargingFull,
                title = "Isključi optimizaciju baterije",
                text = "Kada se pojavi pitanje „Dozvoliti da aplikacija uvek radi u pozadini?“, izaberi Dozvoli.",
                done = p?.batteryUnrestricted == true,
            ) {
                Button(onClick = { PermissionStatus.safeStart(context, PermissionStatus.requestIgnoreBatteryOptimizations(context)) }) {
                    Text("Isključi optimizaciju")
                }
            }

            Step(
                n = 4,
                icon = Icons.Rounded.Bedtime,
                title = "Samsung: „Aplikacije koje nikad ne spavaju“",
                text = "Podešavanja → Baterija → Ograničenja korišćenja u pozadini → Aplikacije koje nikad ne spavaju → + → izaberi „Raspored časova“ → Dodaj.\n\n" +
                    "Na istom ekranu isključi i „Stavi nekorišćene aplikacije u stanje spavanja“ (ili proveri da Raspored časova nije na listi „Aplikacije u stanju spavanja“ / „Aplikacije u dubokom spavanju“).\n\n" +
                    "Dodatno: Podešavanja → Aplikacije → Raspored časova → Baterija → Neograničeno.",
                done = samsungDone,
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { PermissionStatus.openSamsungBatterySettings(context) }) { Text("Baterija") }
                        OutlinedButton(onClick = { PermissionStatus.safeStart(context, PermissionStatus.appDetails(context)) }) { Text("Info o aplikaciji") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = samsungDone, onCheckedChange = onSamsungDone, modifier = Modifier.testTag("samsungDone"))
                        Text("Dodao sam aplikaciju na listu")
                    }
                }
            }

            GlassCard {
                Text("Proba", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pošalji probnu notifikaciju – stiže za tačno 1 minut. Možeš i da zaključaš telefon dok čekaš.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSendTest) { Text("Pošalji test notifikaciju za 1 min") }
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("finishOnboarding"),
            ) { Text(if (firstRun) "Kreni" else "Gotovo") }
            if (firstRun && p?.allGood != true) {
                TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Preskoči za sada (videćeš upozorenje na glavnom ekranu)")
                }
            }
        }
    }
}

@Composable
private fun Step(
    n: Int,
    icon: ImageVector,
    title: String,
    text: String,
    done: Boolean,
    action: @Composable () -> Unit,
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(if (done) OkGreen else MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = Color.Black)
                else Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.textMuted)
        if (!done || n == 4) {
            Spacer(Modifier.height(10.dp))
            action()
        } else {
            Text("✓ Sređeno", color = OkGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
