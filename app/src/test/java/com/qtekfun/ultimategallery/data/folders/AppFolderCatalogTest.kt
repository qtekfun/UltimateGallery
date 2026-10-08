package com.qtekfun.ultimategallery.data.folders

import android.net.Uri
import com.qtekfun.ultimategallery.domain.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFolderCatalogTest {
    private fun folder(id: Long, name: String, path: String?) = Folder(id, name, path, 3, Uri.EMPTY, false, 0)

    private val camera = folder(1, "Camera", "DCIM/Camera/")
    private val wa1 = folder(2, "WhatsApp Images", "Pictures/WhatsApp Images/")
    private val wa2 = folder(3, "Sent", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/")
    private val shots = folder(4, "Screenshots", "Pictures/Screenshots/")
    private val telegram = folder(5, "Telegram", "Pictures/Telegram/")
    private val all = listOf(camera, wa1, wa2, shots, telegram)

    @Test
    fun detectsOnlyAppsThatArePresent() {
        val ids = AppFolderCatalog.detect(all).map { it.entry.id }
        assertEquals(listOf("whatsapp", "telegram", "screenshots"), ids)
    }

    @Test
    fun whatsAppGroupsFoldersByNameAndPath() {
        val wa = AppFolderCatalog.detect(all).first { it.entry.id == AppFolderCatalog.WHATSAPP }
        assertEquals(listOf(wa1, wa2), wa.folders)
    }

    @Test
    fun cameraIsNeverAnAppFolder() {
        assertTrue(AppFolderCatalog.detect(listOf(camera)).isEmpty())
    }

    @Test
    fun hiddenByMapsBucketsToTheirApp() {
        val hidden = AppFolderCatalog.hiddenBy(all, setOf("whatsapp", "screenshots"))
        assertEquals(setOf(2L, 3L, 4L), hidden.keys)
        assertEquals("whatsapp", hidden.getValue(3).id)
        assertTrue(AppFolderCatalog.hiddenBy(all, emptySet()).isEmpty())
    }

    @Test
    fun spanishScreenshotFolderIsRecognised() {
        val es = folder(9, "Capturas de pantalla", "Pictures/Capturas de pantalla/")
        assertEquals("screenshots", AppFolderCatalog.detect(listOf(es)).single().entry.id)
    }
}
