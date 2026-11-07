package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.library.FolderChoice
import net.codekunde.fernplay.android.library.MusicFolders
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFoldersTest {

    private val roots = listOf("/storage/emulated/0", "/storage/1A2B-3C4D")

    @Test
    fun `android 10 relative paths lose their slashes`() {
        assertEquals("Music", MusicFolders.fromRelativePath("Music/"))
        assertEquals("Download/Albums", MusicFolders.fromRelativePath("Download/Albums/"))
        assertEquals("", MusicFolders.fromRelativePath(null))
    }

    @Test
    fun `android 8 file paths match the android 10 form`() {
        assertEquals("Music", MusicFolders.fromFilePath("/storage/emulated/0/Music/Bracken.mp3", roots))
        assertEquals(
            "Download/Albums",
            MusicFolders.fromFilePath("/storage/emulated/0/Download/Albums/Night Bus.mp3", roots),
        )
    }

    @Test
    fun `sd card files are relative to the card`() {
        assertEquals("Music/Live", MusicFolders.fromFilePath("/storage/1A2B-3C4D/Music/Live/Set.flac", roots))
    }

    @Test
    fun `a file at the root of storage has an empty folder`() {
        assertEquals("", MusicFolders.fromFilePath("/storage/emulated/0/Song.mp3", roots))
    }

    @Test
    fun `folder picker ids become folder names`() {
        assertEquals("Music/Live", MusicFolders.fromTreeDocumentId("primary:Music/Live"))
        assertEquals("Music", MusicFolders.fromTreeDocumentId("1A2B-3C4D:Music/"))
        assertEquals("", MusicFolders.fromTreeDocumentId("primary:"))
        assertEquals(null, MusicFolders.fromTreeDocumentId("raw-id"))
    }

    @Test
    fun `a chosen folder includes its subfolders but not look-alikes`() {
        assertEquals(true, MusicFolders.includes("Music", "Music"))
        assertEquals(true, MusicFolders.includes("Music", "Music/Live"))
        assertEquals(false, MusicFolders.includes("Music", "Musical"))
        assertEquals(false, MusicFolders.includes("Music/Live", "Music"))
        assertEquals(true, MusicFolders.includedByAny(listOf("Podcasts", "Download"), "Download/Albums"))
    }

    @Test
    fun `adding a folder in Settings`() {
        // New, unrelated folder: added.
        assertEquals(setOf("Music", "Podcasts"), FolderChoice(setOf("Music")).withAdded("Podcasts")?.chosen)
        // Already chosen, or inside a chosen folder: nothing to change.
        assertEquals(null, FolderChoice(setOf("Music")).withAdded("Music"))
        assertEquals(null, FolderChoice(setOf("Music")).withAdded("Music/Live"))
        // A parent replaces the chosen folders inside it, and only those.
        assertEquals(
            setOf("Download", "Music"),
            FolderChoice(setOf("Download/Albums", "Download/Mixes", "Music")).withAdded("Download")?.chosen,
        )
        // A look-alike name is not a parent.
        assertEquals(setOf("Music", "Musical"), FolderChoice(setOf("Music")).withAdded("Musical")?.chosen)
        // Adding a left-out folder with the picker takes it back in.
        assertEquals(FolderChoice(setOf("Music")), FolderChoice(setOf("Music"), setOf("Music/Telegram")).withAdded("Music/Telegram"))
    }

    @Test
    fun `a subfolder of a ticked folder can be left out`() {
        // First run ticks every folder Android found, parent and subfolder alike.
        val found = FolderChoice(setOf("Music", "Music/Telegram", "Download"))
        val off = found.withOff("Music/Telegram")
        assertEquals(false, off.includes("Music/Telegram"))
        assertEquals(false, off.includes("Music/Telegram/Voice"))
        assertEquals(true, off.includes("Music"))
        assertEquals(true, off.includes("Music/Live"))
        assertEquals(setOf("Music/Telegram"), off.leftOut)
        // Ticked again: back in, nothing left out.
        val on = off.withOn("Music/Telegram")
        assertEquals(true, on.includes("Music/Telegram"))
        assertEquals(emptySet<String>(), on.leftOut)
    }

    @Test
    fun `a folder with no chosen parent is just unticked`() {
        val off = FolderChoice(setOf("Music", "Download")).withOff("Download")
        assertEquals(FolderChoice(setOf("Music")), off)
        assertEquals(FolderChoice(setOf("Music", "Download")), off.withOn("Download"))
    }

    @Test
    fun `the deepest folder wins`() {
        val choice = FolderChoice(setOf("Music", "Music/Telegram/Best"), setOf("Music/Telegram"))
        assertEquals(false, choice.includes("Music/Telegram"))
        assertEquals(true, choice.includes("Music/Telegram/Best"))
        assertEquals(true, choice.includes("Music/Telegram/Best/Live"))
        // The whole storage chosen, one folder left out.
        val all = FolderChoice(setOf(""), setOf("Android"))
        assertEquals(false, all.includes("Android/media"))
        assertEquals(true, all.includes("Music"))
    }

    @Test
    fun `left-out folders go when their parent goes`() {
        val choice = FolderChoice(setOf("Music"), setOf("Music/Telegram"))
        assertEquals(FolderChoice(emptySet()), choice.withOff("Music"))
        assertEquals("Music", choice.parentOf("Music/Telegram"))
        assertEquals(null, choice.parentOf("Music"))
    }

    @Test
    fun `a root that is only a name prefix does not match`() {
        assertEquals(
            "storage/emulated/01/Music",
            MusicFolders.fromFilePath("/storage/emulated/01/Music/Song.mp3", roots),
        )
    }
}
