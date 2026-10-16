@file:OptIn(ExperimentalMaterial3Api::class)

package com.kostaking.raspored.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kostaking.raspored.core.EventType
import com.kostaking.raspored.data.SubjectEntity
import com.kostaking.raspored.ui.theme.BadgeTextStyle
import com.kostaking.raspored.ui.theme.LocalExtraColors
import com.kostaking.raspored.ui.theme.NowRed
import com.kostaking.raspored.ui.theme.contentOn
import kotlinx.coroutines.delay

object Palette {
    val colors: List<Long> = listOf(
        0xFF4C6FFF, 0xFF5AB8FF, 0xFF2EC8E6, 0xFF2BD9A6, 0xFF34C759, 0xFF8BD448,
        0xFFE8E05A, 0xFFFFC23D, 0xFFFF8A3D, 0xFFFF4D6D, 0xFFFF6BC1, 0xFFD06BFF,
        0xFF9B6BFF, 0xFF6F5BFF, 0xFFC79A6B, 0xFF9AA5B8, 0xFF2F80ED, 0xFF00B3A4,
        0xFFB0E0FF, 0xFFFF9AA2, 0xFFFFD6A5, 0xFFB5EAD7, 0xFFC7CEEA, 0xFFE2F0CB,
    )
}

/** Fade + slide-up when an item first appears, staggered by [index]. */
fun Modifier.animateEnter(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * 35).toLong())
        a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 36.dp.toPx()
    }
}

@Composable
fun EventBadge(type: EventType, modifier: Modifier = Modifier, extra: Int = 0) {
    Box(
        modifier
            .background(NowRed, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            "⚠ ${type.badge}" + if (extra > 0) " +$extra" else "",
            style = BadgeTextStyle,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .background(if (filled) color else color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = BadgeTextStyle, color = if (filled) contentOn(color) else color, maxLines = 1)
    }
}

@Composable
fun SubjectDot(color: Color, size: Int = 12) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Plain screen with a back arrow, used by all secondary screens. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Nazad")
                        }
                    },
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            floatingActionButton = floatingActionButton,
            content = content,
        )
    }
}

/** Translucent rounded surface used for list rows on top of the space background. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = LocalExtraColors.current
    val bg = if (extra.isDark) Color(0xCC0E1224) else Color.White
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(bg, shape)
            .border(1.dp, extra.divider, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** Read-only text field that opens a dropdown – avoids the experimental ExposedDropdownMenu API. */
@Composable
fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
    footer: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // Transparent overlay catches the click (a read-only text field swallows it otherwise).
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clickable { expanded = true }
                .testTag("dropdown-$label"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    leadingIcon = leading?.let { { it(opt) } },
                    onClick = {
                        expanded = false
                        onSelect(opt)
                    },
                )
            }
            if (footer != null) {
                HorizontalDivider()
                footer { expanded = false }
            }
        }
    }
}

@Composable
fun SubjectDropdown(
    subjects: List<SubjectEntity>,
    selectedId: Long?,
    onSelect: (SubjectEntity) -> Unit,
    modifier: Modifier = Modifier,
    onNew: (() -> Unit)? = null,
) {
    val selected = subjects.firstOrNull { it.id == selectedId }
    DropdownField(
        label = "Predmet",
        value = selected?.name ?: "",
        options = subjects,
        optionLabel = { it.name },
        onSelect = onSelect,
        modifier = modifier,
        leading = { SubjectDot(Color(it.color)) },
        footer = onNew?.let { cb ->
            { close ->
                DropdownMenuItem(text = { Text("+ Novi predmet…") }, onClick = { close(); cb() })
            }
        },
    )
}

@Composable
fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("U redu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.testTag("confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži") } },
    )
}

/** Palette grid + hue slider for a custom colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Color, onSelect: (Color) -> Unit) {
    var hue by remember { mutableFloatStateOf(hueOf(selected)) }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palette.colors.forEach { c ->
                val color = Color(c)
                val isSel = color.toArgb() == selected.toArgb()
                Box(
                    Modifier
                        .size(36.dp)
                        .background(color, CircleShape)
                        .border(if (isSel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onSelect(color); hue = hueOf(color) }
                        .testTag("color-${"%06X".format(c and 0xFFFFFF)}"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) Icon(Icons.Rounded.Check, null, tint = contentOn(color), modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Prilagođena nijansa", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    onSelect(Color.hsv(it, 0.62f, 1f))
                },
                valueRange = 0f..359f,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(32.dp).background(selected, CircleShape))
        }
    }
}

private fun hueOf(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return hsv[0]
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.textMuted)
        Spacer(Modifier.height(16.dp))
        action()
    }
}
