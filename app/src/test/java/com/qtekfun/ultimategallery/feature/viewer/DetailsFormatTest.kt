package com.qtekfun.ultimategallery.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsFormatTest {
    @Test
    fun sizesAreHumanReadable() {
        assertEquals("512 B", DetailsFormat.size(512))
        assertEquals("1.5 KB", DetailsFormat.size(1536))
        assertEquals("4.0 MB", DetailsFormat.size(4L * 1024 * 1024))
        assertEquals("120 MB", DetailsFormat.size(120L * 1024 * 1024))
        assertEquals("2.5 GB", DetailsFormat.size((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun exposureUsesFractionsBelowOneSecond() {
        assertEquals("1/125 s", DetailsFormat.exposure(0.008))
        assertEquals("2.5 s", DetailsFormat.exposure(2.5))
        assertEquals("", DetailsFormat.exposure(0.0))
    }

    @Test
    fun coordinatesShowHemispheres() {
        assertEquals("40.41680° N, 3.70380° W", DetailsFormat.coordinates(40.4168, -3.7038))
        assertEquals("33.86880° S, 151.20930° E", DetailsFormat.coordinates(-33.8688, 151.2093))
    }

    @Test
    fun durationsHaveHoursOnlyWhenNeeded() {
        assertEquals("0:05", DetailsFormat.duration(5_000))
        assertEquals("1:02:03", DetailsFormat.duration(3_723_000))
    }
}
