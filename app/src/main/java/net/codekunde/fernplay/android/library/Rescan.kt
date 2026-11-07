package net.codekunde.fernplay.android.library

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Asks Android's music index to look at the chosen folders again, for files it
 * has not noticed (copied in a way that skips the index). The library follows
 * the index by itself, so nothing else needs refreshing afterwards.
 */
object Rescan {

    fun folders(context: Context, folders: Collection<String>, done: () -> Unit) {
        val root = Environment.getExternalStorageDirectory()
        val dirs = folders.map { if (it.isEmpty()) root else File(root, it) }.filter { it.isDirectory }
        // Android 10+ scans a folder with everything below it; older versions need each file.
        val paths = if (Build.VERSION.SDK_INT >= 29) dirs.map { it.absolutePath }
        else dirs.flatMap { d -> d.walkTopDown().maxDepth(8).filter { it.isFile }.map { it.absolutePath }.toList() }
        val main = Handler(Looper.getMainLooper())
        if (paths.isEmpty()) {
            main.post(done)
            return
        }
        var left = paths.size
        MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null) { _, _ ->
            main.post { if (--left == 0) done() }
        }
    }
}
