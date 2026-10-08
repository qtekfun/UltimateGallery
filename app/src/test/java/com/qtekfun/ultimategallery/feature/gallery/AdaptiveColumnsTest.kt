package com.qtekfun.ultimategallery.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveColumnsTest {
    @Test
    fun phonesKeepTheirCount() {
        assertEquals(4, adaptiveColumns(4, 360f))
        assertEquals(4, adaptiveColumns(4, 420f))
    }

    @Test
    fun widerWindowsGetProportionallyMoreColumns() {
        assertEquals(8, adaptiveColumns(4, 840f))
        assertEquals(12, adaptiveColumns(4, 1260f))
        assertEquals(4, adaptiveColumns(2, 840f))
    }
}
