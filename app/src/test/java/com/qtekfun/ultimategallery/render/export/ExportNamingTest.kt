package com.qtekfun.ultimategallery.render.export

import com.qtekfun.ultimategallery.render.export.ExportNaming.fileName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNamingTest {
    private val date = LocalDate.of(2024, 3, 9)

    @Test
    fun expandsAllTokens() {
        assertEquals("IMG_001_wm.jpg", fileName("{name}_wm", "IMG_001.jpg", 0, 5, "jpg", date))
        assertEquals("sale_20240309.png", fileName("sale_{date}", "x.heic", 0, 1, "png", date))
    }

    @Test
    fun positionIsOneBasedAndPaddedToTheBatchWidth() {
        assertEquals("p_01.jpg", fileName("p_{n}", "a.jpg", 0, 12, "jpg", date))
        assertEquals("p_12.jpg", fileName("p_{n}", "a.jpg", 11, 12, "jpg", date))
        assertEquals("p_007.jpg", fileName("p_{n}", "a.jpg", 6, 100, "jpg", date))
        assertEquals("p_3.jpg", fileName("p_{n}", "a.jpg", 2, 9, "jpg", date))
    }

    @Test
    fun nameWithoutExtensionAndWithSeveralDots() {
        assertEquals("a.b_wm.webp", fileName("{name}_wm", "a.b.jpg", 0, 1, "webp", date))
        assertEquals("plain_wm.webp", fileName("{name}_wm", "plain", 0, 1, "webp", date))
    }

    @Test
    fun illegalCharactersAreReplaced() {
        assertEquals("a_b_c_d_e_f_g_h_i_j.jpg", fileName("a/b\\c:d*e?f\"g<h>i|j", "x.jpg", 0, 1, "jpg", date))
        assertEquals("a_b.jpg", fileName("a\u0001b", "x.jpg", 0, 1, "jpg", date))
    }

    @Test
    fun blankOrDotOnlyPatternsFallBackToTheOriginalName() {
        assertEquals("photo.jpg", fileName("", "photo.png", 0, 1, "jpg", date))
        assertEquals("photo.jpg", fileName("   ", "photo.png", 0, 1, "jpg", date))
        assertEquals("photo.jpg", fileName("...", "photo.png", 0, 1, "jpg", date))
        assertEquals("image.jpg", fileName("", "", 0, 1, "jpg", date))
    }

    @Test
    fun surroundingSpacesAndTrailingDotsAreTrimmed() {
        assertEquals("a.jpg", fileName("  a. ", "x.jpg", 0, 1, "jpg", date))
    }
}
