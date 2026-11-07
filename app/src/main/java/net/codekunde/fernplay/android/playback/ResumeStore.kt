package net.codekunde.fernplay.android.playback

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps the resume files in [dir] (the app's private files folder). The queue
 * is written only when it changes; the small position file every few seconds.
 * Each write goes to a temporary file first, so a crash never leaves half a file.
 */
class ResumeStore(private val dir: File) {

    private val queueFile get() = File(dir, "resume-queue.txt")
    private val positionFile get() = File(dir, "resume-position.txt")

    fun read(): ResumeState? {
        val queue = queueFile.takeIf { it.isFile }?.let { ResumeCodec.decodeQueue(it.readText()) } ?: return null
        if (queue.items.isEmpty()) return null
        val position = positionFile.takeIf { it.isFile }?.let { ResumeCodec.decodePosition(it.readText()) }
            ?: ResumePosition(queue.items.first().id, 0, 0L, 0)
        return ResumeState(queue, position)
    }

    fun writeQueue(queue: ResumeQueue) = write(queueFile, ResumeCodec.encodeQueue(queue))

    fun writePosition(position: ResumePosition) = write(positionFile, ResumeCodec.encodePosition(position))

    private fun write(target: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
