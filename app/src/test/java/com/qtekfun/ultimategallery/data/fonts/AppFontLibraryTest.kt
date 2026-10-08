package com.qtekfun.ultimategallery.data.fonts

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.FontImportResult
import com.qtekfun.ultimategallery.domain.watermark.FontKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppFontLibraryTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val library = AppFontLibrary(context, Dispatchers.Unconfined)
    private val assets = File("src/main/assets/fonts")

    private fun fontFile(name: String, asset: String = "Pacifico"): Uri {
        val file = File(folder.root, name)
        File(assets, "$asset.ttf").copyTo(file, overwrite = true)
        return Uri.fromFile(file)
    }

    private fun list() = runBlocking { library.fonts.first() }

    private fun importedDir() = File(context.filesDir, "fonts")

    @Test
    fun listsDefaultThenBundledThenImported() {
        runBlocking { library.import(fontFile("Zebra Sans.ttf", "Oswald")) }
        runBlocking { library.import(fontFile("alpha.ttf", "Pacifico")) }
        val fonts = list()
        assertEquals(FontIds.DEFAULT, fonts[0].id)
        assertEquals("Default", fonts[0].label)
        assertEquals(FontKind.DEFAULT, fonts[0].kind)
        val bundled = fonts.filter { it.kind == FontKind.BUNDLED }
        assertEquals(
            listOf("Bebas Neue", "Dancing Script", "Oswald", "Pacifico", "Playfair Display", "Roboto Mono"),
            bundled.map { it.label }
        )
        assertEquals("bundled:PlayfairDisplay", bundled.first { it.label == "Playfair Display" }.id)
        assertEquals(listOf("alpha", "Zebra Sans"), fonts.filter { it.kind == FontKind.IMPORTED }.map { it.label })
        assertEquals(fonts.map { it.kind }, fonts.map { it.kind }.sortedBy { it.ordinal })
    }

    @Test
    fun importsAValidFontIntoPrivateStorage() {
        val result = runBlocking { library.import(fontFile("My Font.ttf")) }
        val font = (result as FontImportResult.Imported).font
        assertEquals("My Font", font.label)
        assertEquals("imported:My Font.ttf", font.id)
        assertEquals(FontKind.IMPORTED, font.kind)
        assertTrue(File(importedDir(), "My Font.ttf").isFile)
        assertNotNull(library.typeface(font.id))
        assertTrue(list().any { it.id == font.id })
    }

    @Test
    fun sanitizesTheStoredName() {
        val font = (runBlocking { library.import(fontFile("we?ird:na*me.ttf")) } as FontImportResult.Imported).font
        assertEquals("we_ird_na_me", font.label)
        assertTrue(File(importedDir(), font.id.removePrefix("imported:")).isFile)
    }

    @Test
    fun garbageIsRejectedAndLeavesNoFile() {
        val junk = File(folder.root, "junk.ttf").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        assertEquals(FontImportResult.Invalid, runBlocking { library.import(Uri.fromFile(junk)) })
        val truncated = File(folder.root, "cut.ttf").apply { writeBytes(File(assets, "Pacifico.ttf").readBytes().copyOf(64)) }
        assertEquals(FontImportResult.Invalid, runBlocking { library.import(Uri.fromFile(truncated)) })
        assertEquals(FontImportResult.Invalid, runBlocking { library.import(Uri.fromFile(File(folder.root, "missing.ttf"))) })
        assertTrue(importedDir().listFiles().orEmpty().isEmpty())
        assertTrue(list().none { it.kind == FontKind.IMPORTED })
    }

    @Test
    fun importingTheSameBytesTwiceIsAlreadyPresent() {
        val first = (runBlocking { library.import(fontFile("a.ttf")) } as FontImportResult.Imported).font
        val second = runBlocking { library.import(fontFile("renamed.ttf")) }
        assertEquals(FontImportResult.AlreadyPresent(first), second)
        assertEquals(1, importedDir().listFiles().orEmpty().size)
    }

    @Test
    fun differentFontsWithTheSameNameGetUniqueFiles() {
        val a = (runBlocking { library.import(fontFile("Same.ttf", "Pacifico")) } as FontImportResult.Imported).font
        val b = (runBlocking { library.import(fontFile("Same.ttf", "Oswald")) } as FontImportResult.Imported).font
        assertTrue(a.id != b.id)
        assertNotNull(library.typeface(a.id))
        assertNotNull(library.typeface(b.id))
    }

    @Test
    fun removeDeletesTheFileAndUpdatesTheList() {
        val font = (runBlocking { library.import(fontFile("Gone.ttf")) } as FontImportResult.Imported).font
        assertNotNull(library.typeface(font.id))
        runBlocking { library.remove(font.id) }
        assertFalse(File(importedDir(), "Gone.ttf").exists())
        assertNull(library.typeface(font.id))
        assertTrue(list().none { it.id == font.id })
    }

    @Test
    fun removeNeverTouchesBundledFontsOrEscapesTheFolder() {
        val before = list()
        runBlocking { library.remove("bundled:Oswald") }
        runBlocking { library.remove(FontIds.DEFAULT) }
        runBlocking { library.remove("imported:../files-secret") }
        assertEquals(before, list())
        assertNotNull(library.typeface("bundled:Oswald"))
    }

    @Test
    fun typefaceLookupFallsBackToNull() {
        assertNotNull(library.typeface("bundled:Pacifico"))
        assertNull(library.typeface(FontIds.DEFAULT))
        assertNull(library.typeface("bundled:DoesNotExist"))
        assertNull(library.typeface("imported:missing.ttf"))
        assertNull(library.typeface("imported:../../etc/passwd"))
        assertNull(library.typeface("nonsense"))
    }
}
