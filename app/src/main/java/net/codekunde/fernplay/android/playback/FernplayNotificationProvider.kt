package net.codekunde.fernplay.android.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Android 12 and older show three buttons in the small notification and on the
 * lock screen. Fernplay puts Repeat, Play/Pause, Next there; Previous stays in
 * the expanded notification. Android 13+ draws its media card from the session.
 */
@OptIn(UnstableApi::class)
class FernplayNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        return compactIndices(
            mediaButtons.map { it.sessionCommand?.customAction to it.playerCommand }
        )
    }

    companion object {
        /** Positions of Repeat, Play/Pause and Next among the buttons, in that order. */
        fun compactIndices(buttons: List<Pair<String?, Int>>): IntArray {
            val repeat = buttons.indexOfFirst { it.first == PlaybackService.ACTION_CYCLE_REPEAT }
            val playPause = buttons.indexOfFirst { it.second == Player.COMMAND_PLAY_PAUSE }
            val next = buttons.indexOfFirst {
                it.second == Player.COMMAND_SEEK_TO_NEXT || it.second == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            }
            return listOf(repeat, playPause, next).filter { it >= 0 }.toIntArray()
        }
    }
}
