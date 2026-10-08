package com.qtekfun.ultimategallery.data.media

import android.net.Uri
import com.qtekfun.ultimategallery.domain.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FileNamesTest {
    @Test
    fun sanitizeStripsSeparatorsAndIllegalCharacters() {
        assertEquals("ab", FileNames.sanitize(" a/b\\:*?\"<>| "))
        assertEquals("hidden", FileNames.sanitize("..hidden"))
        assertEquals("", FileNames.sanitize(" / "))
    }

    @Test
    fun renamedKeepsOrAddsTheOriginalExtension() {
        assertEquals("trip.jpg", FileNames.renamed("IMG_1.jpg", "trip"))
        assertEquals("trip.JPG", FileNames.renamed("IMG_1.jpg", "trip.JPG"))
        assertEquals("v2.final.jpg", FileNames.renamed("IMG_1.jpg", "v2.final"))
        assertEquals("trip", FileNames.renamed("README", "trip"))
        assertNull(FileNames.renamed("IMG_1.jpg", " / "))
    }

    @Test
    fun suffixAndUniqueNames() {
        assertEquals("a_nometa.jpg", FileNames.withSuffix("a.jpg", "_nometa"))
        assertEquals("a_nometa", FileNames.withSuffix("a", "_nometa"))
        assertEquals("a.jpg", FileNames.unique("a.jpg", setOf("b.jpg")))
        assertEquals("a (2).jpg", FileNames.unique("a.jpg", setOf("A.jpg")))
        assertEquals("a (3).jpg", FileNames.unique("a.jpg", setOf("a.jpg", "a (2).jpg")))
    }

    @Test
    fun extensionAndBaseName() {
        assertEquals("jpg", FileNames.extension("a.b.jpg"))
        assertEquals("a.b", FileNames.baseName("a.b.jpg"))
        assertEquals("", FileNames.extension(".nomedia"))
        assertEquals("", FileNames.extension("trailing."))
    }
}

class DestinationsTest {
    @Test
    fun rootsDependOnTheMediaType() {
        assertTrue(Destinations.isValid("Pictures/Trips", isVideo = false))
        assertTrue(Destinations.isValid("DCIM", isVideo = false))
        assertFalse(Destinations.isValid("Movies/Clips", isVideo = false))
        assertTrue(Destinations.isValid("Movies/Clips", isVideo = true))
        assertFalse(Destinations.isValid("Download", isVideo = true))
        assertFalse(Destinations.isValid("Pictures/../Download", isVideo = false))
        assertFalse(Destinations.isValid("", isVideo = false))
    }

    @Test
    fun normalisationAndRelativePath() {
        assertEquals("Pictures/Trips", Destinations.normalize(" /Pictures//Trips/ "))
        assertEquals("Pictures/Trips/", Destinations.toRelativePath("\\Pictures\\Trips"))
    }

    @Test
    fun newFolderPathValidatesTheName() {
        assertEquals("Pictures/Trips", Destinations.newFolderPath("Pictures/", " Trips "))
        assertNull(Destinations.newFolderPath("Pictures", "a/b"))
        assertNull(Destinations.newFolderPath("Pictures", ".."))
        assertNull(Destinations.newFolderPath("Pictures", "  "))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DestinationSuggestionsTest {
    private fun folder(id: Long, path: String?) = Folder(id, "f$id", path, 1, Uri.EMPTY, false, 0)

    @Test
    fun suggestionsAreDistinctSortedAndImageWritable() {
        val folders = listOf(
            folder(1, "Pictures/Zoo/"),
            folder(2, "DCIM/Camera/"),
            folder(3, "Pictures/Zoo"),
            folder(4, "Movies/Clips/"),
            folder(5, null),
            folder(6, "Download/")
        )
        assertEquals(listOf("DCIM/Camera", "Pictures/Zoo"), Destinations.suggestions(folders))
    }
}
