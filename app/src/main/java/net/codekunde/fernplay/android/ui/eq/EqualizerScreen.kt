package net.codekunde.fernplay.android.ui.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.eq.EqFilter
import net.codekunde.fernplay.android.eq.EqPreset
import net.codekunde.fernplay.android.eq.EqSettings
import net.codekunde.fernplay.android.eq.EqualizerState
import net.codekunde.fernplay.android.eq.UserPresets
import net.codekunde.fernplay.android.ui.components.ButtonKind
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernMenu
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.player.LcdGlow
import net.codekunde.fernplay.android.ui.player.LcdPanel
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.LcdText

/**
 * The 10-band EQ: on/off, presets (own and built-in), preamp and ten band
 * sliders, and the curve they make on the LCD. Changes are heard at once.
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val state = EqualizerState.get(LocalContext.current)
    val eq by state.settings.collectAsStateWithLifecycle()
    val own by state.presets.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val current = presetName(eq, own)

    Column(Modifier.fillMaxSize()) {
        FernTopBar(title = "Equalizer", onBack = onBack) { OnSwitch(eq.enabled, state::setEnabled) }

        CurvePanel(eq, current, Modifier.padding(horizontal = 16.dp).fillMaxWidth())

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetsButton(
                own = own,
                current = current,
                onApply = state::apply,
                onDelete = { deleting = it },
                onSave = { saving = true },
                modifier = Modifier.weight(1f),
            )
        }

        Sliders(eq, state, Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp))
    }

    if (saving) SavePresetDialog(own, suggested = own.firstOrNull { eq.matches(it) }?.name.orEmpty(), onDismiss = { saving = false }) {
        state.savePreset(it)
        saving = false
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “$name”?") },
            text = { Text("The preset goes. The EQ stays as it is now.") },
            confirmButton = {
                TextButton(onClick = { state.deletePreset(name); deleting = null }) { Text("Delete", color = Fernplay.colors.amber) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** The name of the preset the sliders show: own presets first, then built-in; null when hand-set. */
private fun presetName(eq: EqSettings, own: List<EqPreset>): String? =
    (own + EqPreset.BUILT_IN).firstOrNull { eq.matches(it) }?.name

@Composable
private fun OnSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = "Equalizer" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (on) "ON" else "OFF", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = if (on) c.text else c.textDim)
        Switch(
            checked = on,
            onCheckedChange = null, // the whole row toggles, word included
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                checkedBorderColor = c.accent,
                uncheckedThumbColor = c.textDim,
                uncheckedTrackColor = c.surface,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/** The LCD: preset name, preamp or OFF, and the curve the EQ gives from 20 Hz to 20 kHz. */
@Composable
private fun CurvePanel(eq: EqSettings, preset: String?, modifier: Modifier) {
    val c = Fernplay.colors
    LcdPanel(modifier, vertical = 12.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (preset ?: "Custom").uppercase(),
                style = LcdText.title.copy(fontSize = 20.sp, lineHeight = 24.sp, shadow = LcdGlow),
                color = if (eq.enabled) c.lcd else c.lcdSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (eq.enabled) "PRE ${EqSettings.dbText(eq.preamp)}" else "EQ OFF",
                style = LcdText.small.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = c.lcdSoft,
                maxLines = 1,
            )
        }
        val lineColor = c.lcd
        val dim = c.lcdDim
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(80.dp)
                .clearAndSetSemantics { },
        ) {
            val w = size.width
            val h = size.height
            fun yOf(db: Double) = (h / 2 - (db.coerceIn(-RANGE, RANGE) / RANGE) * (h / 2 - 3.dp.toPx())).toFloat()
            // Grid: 0 dB across, a mark under each band.
            drawLine(dim, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx())
            for (f in EqSettings.FREQUENCIES) {
                val x = xOf(f) * w
                drawLine(dim, Offset(x, h / 2 - 4.dp.toPx()), Offset(x, h / 2 + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            val steps = 120
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val hz = LOW * (HIGH / LOW).pow(t.toDouble())
                val y = yOf(EqFilter.responseDb(eq, hz))
                if (i == 0) path.moveTo(0f, y) else path.lineTo(t * w, y)
            }
            drawPath(
                path,
                if (eq.enabled) lineColor else dim,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private const val LOW = 20.0
private const val HIGH = 20000.0
private const val RANGE = 15.0 // the curve's top and bottom, in dB (a boost plus preamp can pass 12)

private fun xOf(hz: Double) = (ln(hz / LOW) / ln(HIGH / LOW)).toFloat()

@Composable
private fun PresetsButton(
    own: List<EqPreset>,
    current: String?,
    onApply: (EqPreset) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    val c = Fernplay.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FernButton("Presets", { open = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_tune, kind = ButtonKind.Outline, height = 48.dp)
        FernMenu(expanded = open, onDismiss = { open = false }) {
            if (own.isNotEmpty()) {
                MenuHeading("Your presets")
                own.forEach { p ->
                    PresetItem(p.name, p.name == current, onClick = { onApply(p); open = false }) {
                        FernIconButton(R.drawable.ic_delete, "Delete ${p.name}", { open = false; onDelete(p.name) }, tint = c.textDim)
                    }
                }
                HorizontalDivider(color = c.outline, modifier = Modifier.padding(vertical = 4.dp))
                MenuHeading("Built-in")
            }
            EqPreset.BUILT_IN.forEach { p ->
                PresetItem(p.name, p.name == current, onClick = { onApply(p); open = false })
            }
            HorizontalDivider(color = c.outline, modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Save as…", style = MaterialTheme.typography.bodyLarge, color = c.text) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.textDim) },
                onClick = { open = false; onSave() },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Fernplay.colors.textDim,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A preset line; the one the sliders show carries a check (and says so to TalkBack). */
@Composable
private fun PresetItem(name: String, chosen: Boolean, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = Fernplay.colors
    DropdownMenuItem(
        text = { Text(name, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.accentLight)
            else Spacer(Modifier.size(24.dp))
        },
        trailingIcon = trailing,
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .then(if (chosen) Modifier.semantics { stateDescription = "In use" } else Modifier),
    )
}

/** Preamp, a gap, then the ten bands. */
@Composable
private fun Sliders(eq: EqSettings, state: EqualizerState, modifier: Modifier) {
    val c = Fernplay.colors
    Row(modifier.heightIn(min = 200.dp)) {
        BandSlider(eq.preamp, "PRE", "Preamp", eq.enabled, state::setPreamp, Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 4.dp, vertical = 28.dp).width(1.dp).fillMaxHeight().background(c.outline))
        for (b in 0 until EqSettings.BAND_COUNT) {
            BandSlider(eq.bands[b], EqSettings.label(b), EqSettings.spokenFrequency(b), eq.enabled, { state.setBand(b, it) }, Modifier.weight(1f))
        }
    }
}

/**
 * One vertical slider: value on top, frequency below. Drag anywhere in the
 * column (the finger's height sets the value, in 0.5 dB steps); double-tap
 * puts it back to 0 dB, as double-click does on the desktop. TalkBack sees a
 * slider with a "Reset to 0 dB" action.
 */
@Composable
private fun BandSlider(value: Double, label: String, spoken: String, enabled: Boolean, onChange: (Double) -> Unit, modifier: Modifier) {
    val c = Fernplay.colors
    val change by rememberUpdatedState(onChange)
    val max = EqSettings.MAX_DB
    Column(
        modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                stateDescription = EqSettings.dbText(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), -max.toFloat()..max.toFloat(), steps = 47)
                setProgress { v -> change(snap(v.toDouble())); true }
                customActions = listOf(CustomAccessibilityAction("Reset to 0 dB") { change(0.0); true })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            shortDb(value),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (value == 0.0) c.textDim else c.text,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp),
        )
        val fill = if (enabled) c.accent else c.outline
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0.0) }) }
                .pointerInput(Unit) {
                    val pad = KNOB_H.toPx()
                    fun at(y: Float): Double {
                        val t = ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                        return snap(max - t * 2 * max)
                    }
                    detectVerticalDragGestures(onDragStart = { o -> change(at(o.y)) }) { e, _ ->
                        e.consume()
                        change(at(e.position.y))
                    }
                },
        ) {
            val pad = KNOB_H.toPx()
            val cx = size.width / 2
            val tw = 8.dp.toPx()
            val top = pad
            val span = size.height - 2 * pad
            fun yOf(db: Double) = top + ((max - db) / (2 * max) * span).toFloat()
            // Track, 0 dB tick, fill from 0 to the value, knob.
            drawRoundRect(c.surface, Offset(cx - tw / 2, top - tw / 2), Size(tw, span + tw), CornerRadius(tw / 2))
            val zero = yOf(0.0)
            drawLine(c.outline, Offset(cx - 10.dp.toPx(), zero), Offset(cx + 10.dp.toPx(), zero), strokeWidth = 1.5.dp.toPx())
            val y = yOf(value)
            if (abs(y - zero) > 0.5f) {
                drawRoundRect(fill, Offset(cx - tw / 2, minOf(y, zero)), Size(tw, abs(y - zero)), CornerRadius(tw / 2))
            }
            val kw = minOf(22.dp.toPx(), size.width - 4.dp.toPx())
            val kh = 6.dp.toPx()
            drawRoundRect(c.title, Offset(cx - kw / 2, y - kh / 2), Size(kw, kh), CornerRadius(kh / 2))
        }
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = c.textDim,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).height(20.dp),
        )
    }
}

private val KNOB_H = 8.dp

private fun snap(db: Double) = EqSettings.clamp((db * 2).roundToInt() / 2.0)

/** Value over a slider: "+5", "−2.5", "0" (no unit; the column is narrow). */
private fun shortDb(db: Double): String = EqSettings.dbText(db).removeSuffix(" dB")

/** Asks for a name and saves the EQ as it is now; an own preset's name asks to replace it. */
@Composable
private fun SavePresetDialog(own: List<EqPreset>, suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    var problem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val replaces = UserPresets.replaces(own, name)
    fun submit() {
        problem = UserPresets.nameProblem(name)
        if (problem == null) onSave(name.trim())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", " ").replace("\t", " ").take(UserPresets.MAX_NAME_LENGTH + 1); problem = null },
                label = { Text("Name") },
                singleLine = true,
                isError = problem != null,
                supportingText = {
                    Text(problem ?: if (replaces) "Replaces your preset of this name." else "Saves the preamp and all ten bands.")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = name.isNotBlank()) { Text(if (replaces) "Replace" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
