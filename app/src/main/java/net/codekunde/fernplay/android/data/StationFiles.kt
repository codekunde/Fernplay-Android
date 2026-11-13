package net.codekunde.fernplay.android.data

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A station as a playlist file lists it: the name from `#EXTINF`, if any, and the stream address. */
data class ParsedStation(val name: String?, val url: String)

/**
 * Reads radio stations from `.m3u` and `.m3u8` files, as Fernplay for Windows does:
 * every `http://` or `https://` line is a station, named by the `#EXTINF` line
 * before it. Local file paths and other lines are ignored. No Android in here,
 * so the tests run on the plain JVM.
 */
object StationFiles {
    /** Larger files are refused: a station list is a few kilobytes. */
    const val MAX_BYTES = 1_000_000
    const val MAX_STATIONS = 500
    const val MAX_NAME = 80

    fun isStationFile(fileName: String): Boolean {
        val n = fileName.lowercase()
        return n.endsWith(".m3u") || n.endsWith(".m3u8")
    }

    /** `.m3u8` is UTF-8 by definition; `.m3u` is UTF-8 when it decodes as such, else Windows-1252. */
    fun decode(bytes: ByteArray, fileName: String): String {
        val text = if (fileName.lowercase().endsWith(".m3u8")) {
            String(bytes, Charsets.UTF_8)
        } else {
            strictUtf8(bytes) ?: String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix(BOM)
    }

    /** The byte-order mark some editors put at the start, built from its code (lint flags it as a literal). */
    private val BOM = Char(0xFEFF).toString()

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** The stations in [text], in file order, each address once, at most [MAX_STATIONS]. */
    fun parse(text: String): List<ParsedStation> {
        val out = mutableListOf<ParsedStation>()
        val seen = mutableSetOf<String>()
        var pendingName: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> pendingName = extinfName(line)
                line.startsWith("#") -> Unit
                else -> {
                    if (isStreamUrl(line) && seen.add(line.lowercase())) {
                        out += ParsedStation(pendingName, line)
                        if (out.size == MAX_STATIONS) return out
                    }
                    pendingName = null // a name belongs to the entry right after it
                }
            }
        }
        return out
    }

    fun isStreamUrl(line: String): Boolean {
        if (line.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(line) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }

    /** `#EXTINF:-1 tvg-logo="a,b",Name` → `Name`: the text after the first comma outside quotes. */
    fun extinfName(line: String): String? {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return clean(line.substring(i + 1))
            }
        }
        return null
    }

    /**
     * The name a station gets when the file gave none: the file's own name when it
     * holds this one station only (the usual "one station per file" download),
     * else the server's address.
     */
    fun guessName(url: String, fileName: String, stationsInFile: Int): String {
        if (stationsInFile == 1) {
            val base = fileName.substringBeforeLast('.').trim()
            clean(base)?.let { return it }
        }
        return runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
    }

    /** Trimmed, inner spaces collapsed, at most [MAX_NAME] characters; null when nothing is left. */
    fun clean(name: String): String? {
        val n = name.trim().replace(Regex("\\s+"), " ")
        return n.take(MAX_NAME).trim().ifEmpty { null }
    }
}
