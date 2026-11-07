package net.codekunde.fernplay.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * The queue rules, written against Media3's [Player] so the app's controller
 * and the service share them and tests can run them on a real ExoPlayer.
 * None of these start or stop playback; callers do that.
 */
object QueueEdits {

    /** Makes [items] the queue named [name], at [index]. Shuffle is done by ordering the list, so it is turned off. */
    fun replace(p: Player, items: List<MediaItem>, index: Int, name: String, positionMs: Long = 0L) {
        if (items.isEmpty()) return
        p.shuffleModeEnabled = false
        p.playlistMetadata = MediaItems.queueName(name)
        p.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs.coerceAtLeast(0))
    }

    /**
     * Puts [items] right after the playing song and returns true. With nothing
     * queued it returns false: the caller then plays them as a new queue.
     */
    fun playNext(p: Player, items: List<MediaItem>): Boolean {
        if (p.mediaItemCount == 0) return false
        p.addMediaItems(p.currentMediaItemIndex + 1, items)
        return true
    }

    /** Takes the song at [index] out (never the playing one); returns what [undoRemove] needs. */
    fun remove(p: Player, index: Int): Pair<Int, MediaItem>? {
        if (index !in 0 until p.mediaItemCount || index == p.currentMediaItemIndex) return null
        val item = p.getMediaItemAt(index)
        p.removeMediaItem(index)
        return index to item
    }

    /** Puts a removed song back where it was, or at the end if the queue got shorter. */
    fun undoRemove(p: Player, removed: Pair<Int, MediaItem>) {
        val (index, item) = removed
        p.addMediaItem(index.coerceIn(0, p.mediaItemCount), item)
    }

    /** One tap: Off -> List -> Track -> Off. */
    fun cycleRepeat(p: Player) {
        p.repeatMode = RepeatState.of(p.repeatMode).next.mode
    }

    /** Keeps "Playing from" right after the playing playlist is renamed. */
    fun renameQueue(p: Player, from: String, to: String) {
        if (p.playlistMetadata.title?.toString() == from) p.playlistMetadata = MediaItems.queueName(to)
    }

    /** Loads a saved queue without starting it. */
    fun restore(p: Player, state: ResumeState) {
        replace(p, state.queue.items.map(MediaItems::of), state.startIndex, state.queue.name, state.startPositionMs)
        p.repeatMode = state.position.repeatMode
    }

    /**
     * After a song failed to play: true when the next one should be tried.
     * [errorsInRow] counts this failure, so a queue of only broken files stops
     * after one round instead of looping (repeat List wraps round).
     */
    fun skipAfterError(errorsInRow: Int, p: Player): Boolean =
        errorsInRow < p.mediaItemCount && p.hasNextMediaItem()
}
