package net.codekunde.fernplay.android.library

import java.text.Normalizer
import java.util.Locale

enum class SongSort(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album"),
    Newest("Newest"),
}

/** Sorting, searching and the A–Z strip for the song list. No Android types, so it is unit tested. */
object SongList {

    private val accents = Regex("\\p{Mn}+")

    /** Lower case without accents, so "Café" matches "cafe" and sorts with it. */
    fun fold(text: String): String =
        accents.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** Sorting key without a leading "The " or "A ", as most players do for artists and titles. */
    fun sortKey(text: String): String {
        val f = fold(text).trim()
        return when {
            f.startsWith("the ") -> f.removePrefix("the ")
            f.startsWith("a ") -> f.removePrefix("a ")
            else -> f
        }
    }

    fun keyOf(song: Song, sort: SongSort): String = when (sort) {
        SongSort.Title -> sortKey(song.title)
        SongSort.Artist -> sortKey(song.artist)
        SongSort.Album -> sortKey(song.album)
        SongSort.Newest -> ""
    }

    fun sorted(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
        SongSort.Title -> songs.sortedWith(compareBy({ sortKey(it.title) }, { sortKey(it.artist) }))
        SongSort.Artist -> songs.sortedWith(compareBy({ sortKey(it.artist) }, { sortKey(it.album) }, { sortKey(it.title) }))
        SongSort.Album -> songs.sortedWith(compareBy({ sortKey(it.album) }, { sortKey(it.title) }))
        SongSort.Newest -> songs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenBy { sortKey(it.title) })
    }

    /** Every word of the query must appear in the title, artist or album. */
    fun matches(song: Song, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = fold("${song.title} ${song.artist} ${song.album}")
        return words.all { it in haystack }
    }

    /** The strip's letter for a sort key: A–Z, or "#" for digits and anything else. */
    fun letterOf(key: String): Char {
        val c = key.firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /**
     * Where each letter starts in an already sorted list. Letters with no songs
     * jump to the next letter that has one (or the last song), so every letter
     * on the strip goes somewhere sensible.
     */
    fun letterPositions(sortedSongs: List<Song>, sort: SongSort): Map<Char, Int> {
        if (sortedSongs.isEmpty() || sort == SongSort.Newest) return emptyMap()
        val first = LinkedHashMap<Char, Int>()
        sortedSongs.forEachIndexed { i, s -> first.putIfAbsent(letterOf(keyOf(s, sort)), i) }
        val result = LinkedHashMap<Char, Int>()
        var next = sortedSongs.lastIndex
        for (letter in STRIP.reversed()) {
            first[letter]?.let { next = it }
            result[letter] = next
        }
        return result
    }

    val STRIP: List<Char> = listOf('#') + ('A'..'Z')

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "87 h", "52 min": the total length shown beside a song count. */
    fun totalLength(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60} h" else "$minutes min"
    }
}
