package net.codekunde.fernplay.android.playback

import androidx.annotation.DrawableRes
import androidx.media3.common.Player
import net.codekunde.fernplay.android.R

/** Repeat is one button: each tap goes Off -> List -> Track -> Off. */
enum class RepeatState(val mode: Int, val label: String, @DrawableRes val icon: Int) {
    Off(Player.REPEAT_MODE_OFF, "Off", R.drawable.ic_repeat_off),
    List(Player.REPEAT_MODE_ALL, "List", R.drawable.ic_repeat_list),
    Track(Player.REPEAT_MODE_ONE, "Track", R.drawable.ic_repeat_track);

    val next: RepeatState
        get() = when (this) {
            Off -> List
            List -> Track
            Track -> Off
        }

    companion object {
        fun of(mode: Int): RepeatState = entries.firstOrNull { it.mode == mode } ?: Off
    }
}
