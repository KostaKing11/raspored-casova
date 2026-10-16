package com.kostaking.raspored.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.data.ScheduleState
import com.kostaking.raspored.ui.components.EmptyState
import com.kostaking.raspored.ui.components.animateEnter
import com.kostaking.raspored.ui.theme.LocalExtraColors
import java.time.LocalDate

@Composable
fun UpcomingScreen(
    state: ScheduleState,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    var showPast by rememberSaveable { mutableStateOfFalse() }
    val all = state.timetable.data.events
    val list = all
        .filter { showPast || !it.date.isBefore(today) }
        .sortedWith(compareBy({ it.date }, { it.periodIndex ?: 99 }))
        .let { if (showPast) it.reversed() else it }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Predstojeće", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${all.count { !it.date.isBefore(today) }} obaveza",
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalExtraColors.current.textMuted,
                )
            }
            Text("Prošle", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Switch(checked = showPast, onCheckedChange = { showPast = it })
        }
        if (list.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Rounded.EventAvailable, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary) },
                title = "Nema zakazanih kontrolnih",
                text = "Dodaj kontrolni, test ili domaći – dobićeš podsetnik veče pre i ujutru.",
            ) {
                Button(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Dodaj kontrolni")
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().testTag("upcomingList"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { e ->
                    Column(Modifier.animateEnter(list.indexOf(e))) {
                        EventRow(e, Color(state.color(e.subjectId)), today) { onEdit(e.id) }
                    }
                }
            }
        }
    }
}

private fun mutableStateOfFalse() = androidx.compose.runtime.mutableStateOf(false)
