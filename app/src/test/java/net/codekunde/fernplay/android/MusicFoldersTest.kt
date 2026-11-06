package net.codekunde.fernplay.android

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
    fun `a root that is only a name prefix does not match`() {
        assertEquals(
            "storage/emulated/01/Music",
            MusicFolders.fromFilePath("/storage/emulated/01/Music/Song.mp3", roots),
        )
    }
}
