package net.codekunde.fernplay.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.LcdText

/** The LCD strip at the bottom of Home and All songs. Tap it for Now Playing. */
@Composable
fun MiniPlayer(player: PlayerConnection, onOpen: () -> Unit) {
    val c = Fernplay.colors
    val now by player.now
    val position by player.position
    val n = now ?: return
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(c.lcdBackground)
            .border(1.5.dp, c.lcdFrame, shape),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Open Now Playing", onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArtTile(n.mediaId.toLongOrNull() ?: 0L, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        n.title,
                        style = LcdText.mini.copy(shadow = LcdGlow),
                        color = c.lcd,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        NowPlayingText.miniLine(n.artist, position, n.durationMs),
                        style = LcdText.small,
                        color = c.lcdSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FernIconButton(
                if (n.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (n.isPlaying) "Pause" else "Play",
                player::playPause,
                tint = c.lcd,
                iconSize = 32.dp,
            )
            FernIconButton(R.drawable.ic_next, "Next", player::next, tint = c.lcd)
        }
        MiniProgress(
            if (n.durationMs > 0) position.toFloat() / n.durationMs else 0f,
            Modifier.align(Alignment.BottomStart),
        )
    }
}

/** Home's "carry on" card: the paused queue, where it stands, and one tap to play. */
@Composable
fun ContinueCard(player: PlayerConnection) {
    val c = Fernplay.colors
    val now by player.now
    val position by player.position
    val n = now ?: return
    if (n.isPlaying) return // the mini player already shows it
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .then(if (c.isDark) Modifier else Modifier.shadow(1.dp, shape))
            .clip(shape)
            .background(c.surface)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtTile(2L, size = 56.dp, icon = R.drawable.ic_queue)
        Column(Modifier.weight(1f)) {
            Text(
                "CONTINUE",
                style = MaterialTheme.typography.labelSmall,
                color = c.textDim,
            )
            Text(
                n.queueName.ifEmpty { n.title },
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = c.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                NowPlayingText.continueLine(n.index, n.count, position),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val label = "Resume ${n.queueName.ifEmpty { n.title }}"
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = player::playPause)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(32.dp))
        }
    }
}
