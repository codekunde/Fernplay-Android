package net.codekunde.fernplay.android.playback

/** One queued song as saved for resume: enough to show it and play it without the music index. */
data class ResumeItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/** The queue and where it came from ("All songs", a playlist name). */
data class ResumeQueue(val name: String, val items: List<ResumeItem>)

/** Where playback stood. [mediaId] finds the song again if [index] no longer points at it. */
data class ResumePosition(val mediaId: String, val index: Int, val positionMs: Long, val repeatMode: Int)

data class ResumeState(val queue: ResumeQueue, val position: ResumePosition) {
    /** The queue index to start at: [ResumePosition.index] if it still holds the song, else the song's new place. */
    val startIndex: Int
        get() {
            val items = queue.items
            val i = position.index
            if (i in items.indices && items[i].id == position.mediaId) return i
            val found = items.indexOfFirst { it.id == position.mediaId }
            return if (found >= 0) found else i.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        }

    /** Start position: only kept when the saved song was found. */
    val startPositionMs: Long
        get() = if (queue.items.getOrNull(startIndex)?.id == position.mediaId) position.positionMs.coerceAtLeast(0) else 0L
}

/**
 * Text form of the resume files. Plain lines with tab-separated fields, so a
 * queue of thousands of songs reads in a few milliseconds and tests need no Android.
 * Any damage reads as null: the app then simply starts with nothing queued.
 */
object ResumeCodec {
    private const val QUEUE_HEADER = "fernplay-queue 1"
    private const val POSITION_HEADER = "fernplay-position 1"

    fun encodeQueue(q: ResumeQueue): String = buildString {
        append(QUEUE_HEADER).append('\n')
        append(escape(q.name)).append('\n')
        for (it in q.items) {
            append(listOf(it.id, it.uri, it.title, it.artist, it.album, it.durationMs.toString()).joinToString("\t") { f -> escape(f) })
            append('\n')
        }
    }

    fun decodeQueue(text: String): ResumeQueue? = runCatching {
        val lines = text.split('\n')
        if (lines.firstOrNull() != QUEUE_HEADER || lines.size < 2) return null
        val name = unescape(lines[1])
        val items = lines.drop(2).filter { it.isNotEmpty() }.map { line ->
            val f = line.split('\t').map(::unescape)
            require(f.size == 6)
            ResumeItem(f[0], f[1], f[2], f[3], f[4], f[5].toLong())
        }
        ResumeQueue(name, items)
    }.getOrNull()

    fun encodePosition(p: ResumePosition): String =
        listOf(POSITION_HEADER, escape(p.mediaId), p.index, p.positionMs, p.repeatMode).joinToString("\n", postfix = "\n")

    fun decodePosition(text: String): ResumePosition? = runCatching {
        val lines = text.split('\n')
        if (lines.firstOrNull() != POSITION_HEADER || lines.size < 5) return null
        ResumePosition(unescape(lines[1]), lines[2].toInt(), lines[3].toLong(), lines[4].toInt())
    }.getOrNull()

    fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }

    fun unescape(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}
