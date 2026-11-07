package net.codekunde.fernplay.android.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.playback.SleepRules
import net.codekunde.fernplay.android.playback.SleepState
import net.codekunde.fernplay.android.playback.SleepTimer
import net.codekunde.fernplay.android.ui.theme.Fernplay

/**
 * Sets the sleep timer. Off: the four times and End of this song. Running:
 * the time left, +15 minutes and Turn off. Every choice closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(timer: SleepTimer, onDismiss: () -> Unit) {
    val c = Fernplay.colors
    val state by timer.state.collectAsStateWithLifecycle()
    val remaining by timer.remaining.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun pick(action: () -> Unit) {
        action()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                "Sleep timer",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                color = c.title,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp).semantics { heading() },
            )
            Text(
                when (val s = state) {
                    SleepState.Off -> "Music fades out over the last 30 seconds, then pauses."
                    SleepState.EndOfSong -> "Pauses when this song ends."
                    is SleepState.At -> "Stops in ${SleepRules.clock(remaining ?: 0)}, fading out over the last 30 seconds."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.outline.copy(alpha = 0.5f))
            when (state) {
                SleepState.Off -> {
                    SleepRules.MINUTES.forEach { m ->
                        SheetRow(R.drawable.ic_moon, SleepRules.choiceLabel(m)) { pick { timer.start(m) } }
                    }
                    SheetRow(R.drawable.ic_music, "End of this song") { pick(timer::endOfSong) }
                }
                is SleepState.At -> {
                    SheetRow(R.drawable.ic_add, "${SleepRules.EXTEND_MINUTES} more minutes") { pick(timer::extend) }
                    SheetRow(R.drawable.ic_music, "End of this song instead") { pick(timer::endOfSong) }
                    SheetRow(R.drawable.ic_close, "Turn off", tint = c.amber) { pick(timer::cancel) }
                }
                SleepState.EndOfSong -> {
                    SheetRow(R.drawable.ic_moon, "${SleepRules.EXTEND_MINUTES} minutes instead") { pick(timer::extend) }
                    SheetRow(R.drawable.ic_close, "Turn off", tint = c.amber) { pick(timer::cancel) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(@DrawableRes icon: Int, label: String, tint: Color? = null, onClick: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint ?: c.textDim, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: c.text)
    }
}
