package net.codekunde.fernplay.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.ui.theme.Fernplay

/** 48 dp icon button, as in the design. */
@Composable
fun FernIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = Fernplay.colors.text,
    iconSize: Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 64 dp top bar: optional Back, a title in the title colour, then [trailing]. */
@Composable
fun FernTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showFern: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (showFern) 10.dp else 4.dp),
    ) {
        if (onBack != null) FernIconButton(R.drawable.ic_back, "Back", onBack)
        if (showFern) Icon(painterResource(R.drawable.ic_fern_line), contentDescription = null, tint = c.accentLight, modifier = Modifier.size(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = c.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Fernplay.colors.textDim,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A short dim line inside a list where rows would be ("No playlists yet."). */
@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Fernplay.colors.textDim,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val tileGradients = listOf(
    Color(0xFF2A4E27) to Color(0xFF0D1A10),
    Color(0xFF4A4F62) to Color(0xFF1A1C23),
    Color(0xFF3E6B35) to Color(0xFF12251A),
    Color(0xFF6A5B1F) to Color(0xFF241F0B),
    Color(0xFF2D4B5A) to Color(0xFF0F1A20),
)

/** Stand-in for album art: one of the design's five gradients, picked by [seed], with a fern. */
@Composable
fun ArtTile(seed: Long, size: Dp = 48.dp, @DrawableRes icon: Int = R.drawable.ic_fern_line) {
    val (from, to) = tileGradients[Math.floorMod(seed, tileGradients.size.toLong()).toInt()]
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.21f)).background(Brush.linearGradient(listOf(from, to))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color(0x8CDFF0DA), modifier = Modifier.size(size / 2))
    }
}

/**
 * A song: art, title, "artist · length". The loaded song shows level bars on its
 * tile and a coloured, heavier title (the bars carry it without colour).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
    endPadding: Dp = 16.dp,
    onLongClick: (() -> Unit)? = null,
    /** The row's ⋮ menu lines; `close` shuts the menu. No ⋮ button when null. */
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val c = Fernplay.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(
                onClickLabel = "Play",
                onClick = onClick,
                onLongClickLabel = if (onLongClick != null) "Select" else null,
                onLongClick = onLongClick,
            )
            .then(if (playing) Modifier.semantics { stateDescription = "Now playing" } else Modifier)
            // The ⋮ button brings its own 12 dp of space on each side.
            .padding(start = 16.dp, end = if (menu != null) (endPadding - 12.dp).coerceAtLeast(4.dp) else endPadding, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtTile(song.id, icon = if (playing) R.drawable.ic_now_playing else R.drawable.ic_fern_line)
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                // Light theme's title colour equals the text colour, so the mark uses the accent there.
                color = if (!playing) c.text else if (c.isDark) c.title else c.accent,
                fontWeight = if (playing) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} · ${SongList.duration(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (menu != null) {
            Box {
                FernIconButton(R.drawable.ic_more, "More for ${song.title}", { menuOpen = true }, tint = c.textDim)
                FernMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) { menu { menuOpen = false } }
            }
        }
    }
}

/** Centred message for empty and error states, with an optional action below. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    val c = Fernplay.colors
    Column(
        modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(56.dp)).background(c.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = c.onAccentContainer, modifier = Modifier.size(56.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.title, modifier = Modifier.semantics { heading() })
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action()
    }
}

/** A tappable list row with a leading icon, used for "All songs" and "Choose another folder". */
@Composable
fun NavRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailingIcon: Int? = R.drawable.ic_chevron,
) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
        }
        if (trailingIcon != null) Icon(painterResource(trailingIcon), contentDescription = null, tint = c.textDim)
    }
}
