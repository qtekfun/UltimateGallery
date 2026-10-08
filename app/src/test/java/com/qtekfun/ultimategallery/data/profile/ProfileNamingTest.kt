package com.qtekfun.ultimategallery.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNamingTest {
    @Test
    fun cleanTrimsCollapsesAndLimitsTheName() {
        assertEquals("My shop", ProfileNaming.clean("  My   shop \n"))
        assertEquals(40, ProfileNaming.clean("x".repeat(100)).length)
    }

    @Test
    fun copyNameAddsASuffixAndAvoidsExistingNames() {
        assertEquals("Default copy", ProfileNaming.copyName("Default", listOf("Default")))
        assertEquals("Default copy 2", ProfileNaming.copyName("Default", listOf("Default", "default COPY")))
        assertEquals("Default copy 3", ProfileNaming.copyName("Default", listOf("Default copy", "Default copy 2")))
    }
}
