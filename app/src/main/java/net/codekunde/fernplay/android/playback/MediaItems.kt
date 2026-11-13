package net.codekunde.fernplay.android.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import net.codekunde.fernplay.android.data.StationEntity
import net.codekunde.fernplay.android.library.Song

/**
 * Songs, stations and saved queue entries as player items, and back. The song
 * length rides in [MediaMetadata.durationMs], which Media3 marks unstable.
 *
 * A station's name rides in [MediaMetadata.albumTitle] (car and Bluetooth
 * displays show it as the album), so its title and artist are free for the live
 * song; [MediaMetadata.station] is left to the stream's own icy-name.
 */
@OptIn(UnstableApi::class)
object MediaItems {

    fun of(song: Song): MediaItem =
        build(song.id.toString(), song.uri, song.title, song.artist, song.album, song.durationMs)

    fun of(station: StationEntity): MediaItem = station(RadioRules.mediaId(station.id), station.url, station.name)

    fun of(item: ResumeItem): MediaItem =
        if (RadioRules.isStation(item.id)) station(item.id, item.uri, item.album.ifEmpty { item.title })
        else build(item.id, item.uri, item.title, item.artist, item.album, item.durationMs)

    fun toResume(item: MediaItem): ResumeItem {
        val m = item.mediaMetadata
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (RadioRules.isStation(item.mediaId)) {
            // The live song is not saved: a resumed station starts with its own name.
            val name = m.albumTitle?.toString().orEmpty()
            return ResumeItem(item.mediaId, uri, name, "", name, 0L)
        }
        return ResumeItem(
            id = item.mediaId,
            uri = uri,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString().orEmpty(),
            album = m.albumTitle?.toString().orEmpty(),
            durationMs = m.durationMs ?: 0L,
        )
    }

    /** The station's own name (see the class comment), or "" for a song. */
    fun stationName(item: MediaItem?): String =
        if (RadioRules.isStation(item?.mediaId)) item!!.mediaMetadata.albumTitle?.toString().orEmpty() else ""

    /** [item] with the live song in its title and artist; the address and name stay. */
    fun withLiveTitle(item: MediaItem, live: LiveTitle): MediaItem =
        item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(live.title).setArtist(live.artist).build())
            .build()

    /** [item] under a new station name; a title that was the old name follows it. */
    fun renamed(item: MediaItem, name: String): MediaItem {
        val m = item.mediaMetadata
        val old = m.albumTitle?.toString()
        val b = m.buildUpon().setAlbumTitle(name)
        if (m.title?.toString() == old) b.setTitle(name)
        if (m.artist?.toString() == old) b.setArtist(name)
        return item.buildUpon().setMediaMetadata(b.build()).build()
    }

    fun queueName(name: String): MediaMetadata = MediaMetadata.Builder().setTitle(name).build()

    private fun station(id: String, url: String, name: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("")
                    .setAlbumTitle(name)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()

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