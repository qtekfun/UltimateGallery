package com.qtekfun.ultimategallery

import org.junit.Assert.assertTrue
import org.junit.Test

class VersionSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.qtekfun.ultimategallery"))
    }
}
