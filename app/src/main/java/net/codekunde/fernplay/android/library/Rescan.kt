package net.codekunde.fernplay.android.library

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File

/** How far a rescan has got: [done] of [total] paths checked. */
data class RescanProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
}

/**
 * Asks Android's music index to look at the chosen folders again, for files it
 * has not noticed (copied in a way that skips the index). The library follows
 * the index by itself, so nothing else needs refreshing afterwards.
 */
object Rescan {

    /**
     * [progress] and [done] run on the main thread. Android 10+ scans a folder
     * with everything below it, so progress counts folders there and files on
     * Android 8 and 9.
     */
    fun folders(
        context: Context,
        folders: Collection<String>,
        progress: (RescanProgress) -> Unit = {},
        done: () -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        // Listing files on Android 8/9 can take a moment: keep it off the main thread.
        Thread {
            val root = Environment.getExternalStorageDirectory()
            val dirs = folders.map { if (it.isEmpty()) root else File(root, it) }.filter { it.isDirectory }
            val paths = if (Build.VERSION.SDK_INT >= 29) dirs.map { it.absolutePath }
            else dirs.flatMap { d -> d.walkTopDown().maxDepth(8).filter { it.isFile }.map { it.absolutePath }.toList() }
            if (paths.isEmpty()) {
                main.post(done)
                return@Thread
            }
            main.post { progress(RescanProgress(0, paths.size)) }
            var checked = 0
            MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null) { _, _ ->
                main.post {
                    checked++
                    progress(RescanProgress(checked, paths.size))
                    if (checked == paths.size) done()
                }
            }
        }.start()
    }
}
