package net.codekunde.fernplay.android.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import net.codekunde.fernplay.android.library.Song

/**
 * Songs and saved queue entries as player items, and back. The song length
 * rides in [MediaMetadata.durationMs], which Media3 marks unstable.
 */
@OptIn(UnstableApi::class)
object MediaItems {

    fun of(song: Song): MediaItem =
        build(song.id.toString(), song.uri, song.title, song.artist, song.album, song.durationMs)

    fun of(item: ResumeItem): MediaItem =
        build(item.id, item.uri, item.title, item.artist, item.album, item.durationMs)

    fun toResume(item: MediaItem): ResumeItem {
        val m = item.mediaMetadata
        return ResumeItem(
            id = item.mediaId,
            uri = item.localConfiguration?.uri?.toString().orEmpty(),
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString().orEmpty(),
            album = m.albumTitle?.toString().orEmpty(),
            durationMs = m.durationMs ?: 0L,
        )
    }

    fun queueName(name: String): MediaMetadata = MediaMetadata.Builder().setTitle(name).build()

    private fun build(id: String, uri: String, title: String, artist: String, album: String, durationMs: Long): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    // Known before the file is opened, so a restored queue shows its length at once.
                    .setDurationMs(durationMs.takeIf { it > 0 })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
}
