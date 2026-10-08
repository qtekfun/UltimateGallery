package com.qtekfun.ultimategallery.data.media

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAccessRulesTest {
    private fun level(sdk: Int, vararg granted: String) = MediaAccessRules.level(sdk) { it in granted }

    @Test
    fun android14FullAccessNeedsImagesAndVideo() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(
            MediaAccessLevel.FULL,
            level(sdk, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        )
    }

    @Test
    fun android14SelectedPhotosOnlyIsPartial() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(MediaAccessLevel.PARTIAL, level(sdk, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        assertEquals(
            MediaAccessLevel.PARTIAL,
            level(sdk, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        )
    }

    @Test
    fun noPermissionIsNone() {
        assertEquals(MediaAccessLevel.NONE, level(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertEquals(MediaAccessLevel.NONE, level(Build.VERSION_CODES.TIRAMISU))
        assertEquals(MediaAccessLevel.NONE, level(Build.VERSION_CODES.S))
    }

    @Test
    fun android13AndOlderUseTheirOwnPermissions() {
        assertEquals(MediaAccessLevel.FULL, level(Build.VERSION_CODES.TIRAMISU, Manifest.permission.READ_MEDIA_IMAGES))
        assertEquals(MediaAccessLevel.FULL, level(Build.VERSION_CODES.S, Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    @Test
    fun requestedPermissionsMatchTheSdk() {
        assertTrue(
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in
                MediaAccessRules.permissionsToRequest(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertTrue(
            Manifest.permission.READ_EXTERNAL_STORAGE in MediaAccessRules.permissionsToRequest(Build.VERSION_CODES.S)
        )
    }
}
