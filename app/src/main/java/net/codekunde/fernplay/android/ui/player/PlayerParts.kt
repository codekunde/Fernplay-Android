package net.codekunde.fernplay.android.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.RepeatState
import net.codekunde.fernplay.android.ui.theme.Fernplay

/** The green glow on the LCD's main text. */
val LcdGlow = Shadow(color = Color(0x594BE868), blurRadius = 14f)

/** Dark LCD panel (same in both themes): rim, faint inner glow, green text. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, vertical: Dp = 14.dp, content: @Composable () -> Unit) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.lcdBackground)
            .border(2.dp, c.lcdFrame, shape)
            .padding(horizontal = 18.dp, vertical = vertical),
    ) { content() }
}

/**
 * The seek bar from the design: thick track, green fill, a tall title-coloured
 * thumb. Tap or drag to seek; [onScrub] reports the place while the finger is down
 * (null when lifted), so the clock can follow it. TalkBack sees a slider.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: (Long?) -> Unit,
    big: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = Fernplay.colors
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackH = if (big) 16.dp else 8.dp
    val thumbW = if (big) 10.dp else 6.dp
    val thumbH = if (big) 52.dp else 36.dp
    val enabled = durationMs > 0
    fun at(x: Float, width: Int) = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    fun ms(f: Float) = (f * durationMs).toLong()

    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (big) 64.dp else 48.dp)
            .semantics {
                contentDescription = "Seek"
                stateDescription = "${SongList.duration(ms(fraction))} of ${SongList.duration(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) setProgress { v -> onSeek(ms(v.coerceIn(0f, 1f))); true }
            }
            .pointerInput(durationMs) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { o -> onSeek(ms(at(o.x, size.width))) })
            }
            .pointerInput(durationMs) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = at(o.x, size.width); onScrub(ms(drag!!)) },
                    onDragEnd = { drag?.let { onSeek(ms(it)) }; drag = null; onScrub(null) },
                    onDragCancel = { drag = null; onScrub(null) },
                ) { change, _ ->
                    change.consume()
                    drag = at(change.position.x, size.width)
                    onScrub(ms(drag!!))
                }
            },
    ) {
        val th = trackH.toPx()
        val top = (size.height - th) / 2
        val r = CornerRadius(th / 2)
        drawRoundRect(c.surface, Offset(0f, top), Size(size.width, th), r)
        drawRoundRect(c.accent, Offset(0f, top), Size(size.width * fraction, th), r)
        val tw = thumbW.toPx()
        val tH = thumbH.toPx()
        drawRoundRect(
            c.title, // amber-white in dark, near-black in light, as the design's --ttl
            Offset(size.width * fraction - tw / 2, (size.height - tH) / 2),
            Size(tw, tH),
            CornerRadius(tw / 2),
        )
    }
}

/**
 * A big square-ish button (transport). [filled] is the green Play/Pause, the
 * others sit on the surface colour.
 */
@Composable
fun PadButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    radius: Dp,
    filled: Boolean = false,
    iconSize: Dp = 44.dp,
) {
    val c = Fernplay.colors
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(if (filled) c.accent else c.surface)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (filled) c.onAccent else c.text, modifier = Modifier.size(iconSize))
    }
}

/**
 * Repeat as one button: Off -> List -> Track. The state shows three ways: the
 * word, the icon (a "1" for Track) and the fill; colour is never the only cue.
 */
@Composable
fun RepeatToggle(
    state: RepeatState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    radius: Dp = 20.dp,
    fontSize: TextUnit = 12.sp,
    iconSize: Dp = 24.dp,
) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(radius)
    val (bg, border, fg) = when (state) {
        RepeatState.Off -> Triple(Color.Transparent, c.outline, c.textDim)
        RepeatState.List -> Triple(c.accentContainer, c.accentLight, c.onAccentContainer)
        RepeatState.Track -> Triple(c.amber, Color.Transparent, c.onAmber)
    }
    Column(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .border(2.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = NowPlayingText.repeatDescription(state) }
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val icon = if (state == RepeatState.Track) R.drawable.ic_repeat_track else R.drawable.ic_repeat_list
        Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
        Text(
            NowPlayingText.repeatLabel(state),
            style = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.15f, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

/** The plain toggle-shaped button beside Repeat (QUEUE): outline, icon over a label. */
@Composable
fun LabelButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .height(64.dp)
            .clip(shape)
            .border(2.dp, c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.text, modifier = Modifier.size(24.dp))
        Text(
            label.uppercase(),
            style = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = c.text,
            maxLines = 1,
        )
    }
}

/** Thin progress line along the bottom of the mini player. */
@Composable
fun MiniProgress(fraction: Float, modifier: Modifier = Modifier) {
    val c = Fernplay.colors
    Box(modifier.fillMaxWidth().height(3.dp).background(c.lcdDim)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(c.lcd))
    }
}

/** Space reserved where the design has controls that come later (queue, crossfade). */
@Composable
fun Gap48() = Box(Modifier.size(48.dp))
