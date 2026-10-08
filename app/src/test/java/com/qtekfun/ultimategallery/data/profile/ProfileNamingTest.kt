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
        assertEquals("Wallapop copy", ProfileNaming.copyName("Wallapop", listOf("Wallapop")))
        assertEquals("Wallapop copy 2", ProfileNaming.copyName("Wallapop", listOf("Wallapop", "wallapop COPY")))
        assertEquals("Wallapop copy 3", ProfileNaming.copyName("Wallapop", listOf("Wallapop copy", "Wallapop copy 2")))
    }
}
