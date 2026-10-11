package com.qtekfun.ultimategallery.data.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperGeometryTest {
    private val eps = 1e-4f

    private fun assertCrop(expected: CropFraction, actual: CropFraction) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun aLandscapePhotoIsCenteredAndCutAtTheSidesOnAPortraitScreen() {
        // 4000x3000 on 1000x2000: covered at scale 2/3 -> shown 2666.7 x 2000, a 1000 wide window in the middle.
        val crop = WallpaperGeometry.crop(4000f, 3000f, 1000f, 2000f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        assertCrop(CropFraction(0.3125f, 0f, 0.6875f, 1f), crop)
    }

    @Test
    fun panningToTheEdgeShowsThatEdge() {
        val (maxX, maxY) = WallpaperGeometry.maxOffset(4000f, 3000f, 1000f, 2000f, zoom = 1f)
        assertEquals(0f, maxY, eps)
        // Moving the photo right reveals its left edge.
        val crop = WallpaperGeometry.crop(4000f, 3000f, 1000f, 2000f, zoom = 1f, offsetX = maxX, offsetY = 0f)
        assertCrop(CropFraction(0f, 0f, 0.375f, 1f), crop)
    }

    @Test
    fun zoomingNarrowsTheCropAroundTheCenter() {
        val crop = WallpaperGeometry.crop(1000f, 2000f, 1000f, 2000f, zoom = 2f, offsetX = 0f, offsetY = 0f)
        assertCrop(CropFraction(0.25f, 0.25f, 0.75f, 0.75f), crop)
    }

    @Test
    fun offsetsBeyondTheBoundsAreClamped() {
        val crop = WallpaperGeometry.crop(1000f, 2000f, 1000f, 2000f, zoom = 2f, offsetX = -1e6f, offsetY = 1e6f)
        assertCrop(CropFraction(0.5f, 0f, 1f, 0.5f), crop)
    }

    @Test
    fun theDecodeShrinksTheCropToJustCoverTheScreen() {
        // The crop is 1500x4000 source pixels; covering a 1080x2400 screen needs a 0.72 scale (width decides).
        val plan = WallpaperGeometry.decodePlan(4000, 4000, CropFraction(0.25f, 0f, 0.625f, 1f), 1080, 2400)
        assertEquals(DecodePlan(2880, 2880, 720, 0, 1800, 2880), plan)
    }

    @Test
    fun aSmallPhotoIsNeverEnlarged() {
        val plan = WallpaperGeometry.decodePlan(800, 600, CropFraction.FULL, 1080, 2400)
        assertEquals(DecodePlan(800, 600, 0, 0, 800, 600), plan)
    }
}
