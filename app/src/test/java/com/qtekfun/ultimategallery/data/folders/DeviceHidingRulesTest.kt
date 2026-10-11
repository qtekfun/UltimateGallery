package com.qtekfun.ultimategallery.data.folders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHidingRulesTest {
    @Test
    fun subfoldersCanBeHidden() {
        assertTrue(DeviceHidingRules.canHide("Pictures/Trips/"))
        assertTrue(DeviceHidingRules.canHide("DCIM/Camera/"))
        assertTrue(DeviceHidingRules.canHide("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertTrue(DeviceHidingRules.canHide("MyScans/"))
    }

    @Test
    fun theRootAndStandardTopLevelFoldersCannot() {
        assertFalse(DeviceHidingRules.canHide(null))
        assertFalse(DeviceHidingRules.canHide(""))
        assertFalse(DeviceHidingRules.canHide("/"))
        assertFalse(DeviceHidingRules.canHide("DCIM/"))
        assertFalse(DeviceHidingRules.canHide("Pictures"))
        assertFalse(DeviceHidingRules.canHide("download/"))
        assertFalse(DeviceHidingRules.canHide("Android/"))
    }

    @Test
    fun privateAppFoldersCannot() {
        assertFalse(DeviceHidingRules.canHide("Android/data/com.example/files/"))
        assertFalse(DeviceHidingRules.canHide("Android/OBB/com.example/"))
    }

    @Test
    fun normalizeDropsEmptySegments() {
        assertEquals("Pictures/Trips", DeviceHidingRules.normalize("/Pictures//Trips/"))
    }
}
