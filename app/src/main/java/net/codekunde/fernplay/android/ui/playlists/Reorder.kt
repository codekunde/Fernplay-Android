package net.codekunde.fernplay.android.ui.playlists

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Drag-to-reorder for a LazyColumn whose items are all reorderable rows.
 * The dragged row follows the finger; when its middle passes another row the
 * two swap through [onMove]. Near the top or bottom edge the list scrolls.
 */
class ReorderState(val list: LazyListState, private val onMove: (from: Int, to: Int) -> Unit) {

    /** Key of the row being dragged, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var draggingIndex by mutableStateOf<Int?>(null)
    private var delta by mutableFloatStateOf(0f)
    private var startOffset = 0
    private var edge by mutableFloatStateOf(0f)

    private val dragged: LazyListItemInfo?
        get() = draggingIndex?.let { i -> list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i } }

    /** How far the dragged row is drawn from its place in the list. */
    val offset: Float
        get() = dragged?.let { startOffset + delta - it.offset } ?: 0f

    fun start(key: Any) {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        draggingIndex = info.index
        startOffset = info.offset
        delta = 0f
    }

    fun drag(dy: Float) {
        delta += dy
        val info = dragged ?: return
        val top = info.offset + offset
        val bottom = top + info.size
        val middle = ((top + bottom) / 2).toInt()
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != info.index && middle in it.offset until it.offset + it.size
        }
        if (target != null) {
            onMove(info.index, target.index)
            draggingIndex = target.index
        }
        val layout = list.layoutInfo
        edge = when {
            bottom > layout.viewportEndOffset -> bottom - layout.viewportEndOffset
            top < layout.viewportStartOffset -> top - layout.viewportStartOffset
            else -> 0f
        }
    }

    fun end() {
        draggingKey = null
        draggingIndex = null
        delta = 0f
        edge = 0f
    }

    /** Scrolls while the row is held past an edge. */
    suspend fun autoScroll() {
        while (draggingKey != null) {
            withFrameNanos { }
            val e = edge
            if (e != 0f) {
                list.scrollBy((e * 0.2f).coerceIn(-28f, 28f))
                drag(0f)
            }
        }
    }
}

@Composable
fun AutoScrollWhileDragging(state: ReorderState) {
    LaunchedEffect(state.draggingKey) { if (state.draggingKey != null) state.autoScroll() }
}
