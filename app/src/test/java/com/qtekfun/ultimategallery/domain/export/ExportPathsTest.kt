package com.qtekfun.ultimategallery.domain.export

import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPathsTest {
    @Test
    fun normalizeTrimsAndCollapsesSlashes() {
        assertEquals("Pictures/Wallapop", ExportPaths.normalize("  /Pictures//Wallapop/ "))
        assertEquals("", ExportPaths.normalize("///"))
    }

    @Test
    fun onlyFoldersUnderPicturesOrDcimAreValid() {
        assertTrue(ExportPaths.isValidDestination("Pictures/Wallapop"))
        assertTrue(ExportPaths.isValidDestination("/DCIM/Shop/Items/"))
        assertFalse(ExportPaths.isValidDestination("Pictures"))
        assertFalse(ExportPaths.isValidDestination("Download/Wallapop"))
        assertFalse(ExportPaths.isValidDestination("Pictures/../Android"))
        assertFalse(ExportPaths.isValidDestination(""))
    }

    @Test
    fun exampleNameFollowsThePatternAndFormat() {
        assertEquals("IMG_0042_wm.jpg", ExportPaths.exampleName(ExportSettings()))
        assertEquals(
            "shop-01-20261008.webp",
            ExportPaths.exampleName(ExportSettings(format = ExportFormat.WEBP, fileNamePattern = "shop-{n}-{date}"))
        )
        assertEquals("IMG_0042.png", ExportPaths.exampleName(ExportSettings(format = ExportFormat.PNG, fileNamePattern = "")))
    }
}
