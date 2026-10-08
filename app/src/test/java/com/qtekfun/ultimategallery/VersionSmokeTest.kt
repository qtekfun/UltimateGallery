package com.qtekfun.ultimategallery

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.qtekfun.ultimategallery", BuildConfig.APPLICATION_ID)
    }
}
