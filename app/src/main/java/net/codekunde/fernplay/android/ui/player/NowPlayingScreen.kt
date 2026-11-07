package net.codekunde.fernplay.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.playback.NowPlaying
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.LcdText

/** Full-screen player: art, LCD, seek, transport, Repeat. Controls sit in thumb reach. */
@Composable
fun NowPlayingScreen(player: PlayerConnection, onClose: () -> Unit, onGym: () -> Unit) {
    val c = Fernplay.colors
    val now by player.now
    val position by player.position
    var scrub by remember { mutableStateOf<Long?>(null) }
    val n = now ?: return NothingPlaying(onClose)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The art gives way on short screens; everything below it keeps its size.
        val art = min(maxWidth * 0.62f, maxHeight - 450.dp).coerceAtLeast(96.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FernIconButton(R.drawable.ic_down, "Close Now Playing", onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = c.textDim)
                    Text(
                        n.queueName.ifEmpty { "Fernplay" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = c.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                FernIconButton(R.drawable.ic_more, "Gym view", onGym)
            }

            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                ArtTile(n.mediaId.toLongOrNull() ?: 0L, size = art)
            }

            NowPlayingLcd(n, scrub ?: position, Modifier.padding(horizontal = 16.dp).fillMaxWidth())

            SeekBar(
                positionMs = position,
                durationMs = n.durationMs,
                onSeek = player::seekTo,
                onScrub = { scrub = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadButton(R.drawable.ic_prev, "Previous", player::previous, Modifier.size(72.dp), 24.dp)
                PadButton(
                    if (n.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (n.isPlaying) "Pause" else "Play",
                    player::playPause,
                    Modifier.size(84.dp),
                    30.dp,
                    filled = true,
                )
                PadButton(R.drawable.ic_next, "Next", player::next, Modifier.size(72.dp), 24.dp)
            }

            // XFADE (v2) and QUEUE (stage 5) join Repeat in this row later.
            RepeatToggle(n.repeat, player::cycleRepeat, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NowPlayingLcd(n: NowPlaying, positionMs: Long, modifier: Modifier) {
    val c = Fernplay.colors
    LcdPanel(modifier) {
        Text(n.title, style = LcdText.title.copy(shadow = LcdGlow), color = c.lcd, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${n.artist.uppercase()} · ${NowPlayingText.trackOf(n.index, n.count)}",
            style = LcdText.small.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = c.lcdSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LcdClock(positionMs, n.durationMs, big = false, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Elapsed (big, left) and time left (small, right) on one baseline. */
@Composable
private fun LcdClock(positionMs: Long, durationMs: Long, big: Boolean, modifier: Modifier = Modifier) {
    val c = Fernplay.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            NowPlayingText.elapsed(positionMs),
            style = LcdText.time.let { if (big) it.copy(fontSize = 84.sp, lineHeight = 88.sp) else it }.copy(shadow = LcdGlow),
            color = c.lcd,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        Text(
            NowPlayingText.remaining(positionMs, durationMs),
            style = LcdText.small.copy(fontSize = if (big) 24.sp else 18.sp, lineHeight = if (big) 28.sp else 22.sp),
            color = c.lcdSoft,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Gym view: the same player with everything big enough to hit mid-set from arm's
 * length. No queue editing here, so a stray tap cannot change the list.
 */
@Composable
fun GymScreen(player: PlayerConnection, onClose: () -> Unit) {
    val c = Fernplay.colors
    val now by player.now
    val position by player.position
    var scrub by remember { mutableStateOf<Long?>(null) }
    val n = now ?: return NothingPlaying(onClose)
    val shown = scrub ?: position

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FernIconButton(R.drawable.ic_down, "Leave gym view", onClose)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "GYM VIEW",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = c.onAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.amber)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .semantics { heading() },
                )
            }
            Gap48() // the queue button comes with the queue view (stage 5)
        }

        LcdPanel(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp).fillMaxWidth(), vertical = 16.dp) {
            Text(
                n.title,
                style = LcdText.title.copy(fontSize = 34.sp, lineHeight = 40.sp, shadow = LcdGlow),
                color = c.lcd,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                n.artist.uppercase(),
                style = LcdText.small.copy(fontSize = 19.sp, lineHeight = 24.sp),
                color = c.lcdSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LcdClock(shown, n.durationMs, big = true, modifier = Modifier.padding(top = 8.dp))
        }

        SeekBar(
            positionMs = position,
            durationMs = n.durationMs,
            onSeek = player::seekTo,
            onScrub = { scrub = it },
            big = true,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        )

        Spacer(Modifier.weight(1f))

        RepeatToggle(
            n.repeat,
            player::cycleRepeat,
            Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            height = 88.dp,
            radius = 26.dp,
            fontSize = 16.sp,
            iconSize = 32.dp,
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PadButton(R.drawable.ic_prev, "Previous", player::previous, Modifier.width(96.dp).height(128.dp), 32.dp)
            PadButton(
                if (n.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (n.isPlaying) "Pause" else "Play",
                player::playPause,
                Modifier.weight(1f).height(128.dp),
                36.dp,
                filled = true,
                iconSize = 64.dp,
            )
            PadButton(R.drawable.ic_next, "Next", player::next, Modifier.width(96.dp).height(128.dp), 32.dp)
        }
    }
}

@Composable
private fun NothingPlaying(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FernTopBar(title = "Now playing", onBack = onClose)
        EmptyState(
            icon = R.drawable.ic_music,
            title = "Nothing playing",
            text = "Pick a song in All songs and it plays here.",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
