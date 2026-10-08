package com.qtekfun.ultimategallery.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** How much of the device's media this app may read. */
enum class MediaAccessLevel {
    /** Everything. */
    FULL,

    /** Only the photos and videos the user picked (Android 14+ "selected photos only"). */
    PARTIAL,
    NONE
}

/** Pure mapping from granted permissions to an access level, kept separate to be unit-testable. */
object MediaAccessRules {
    fun permissionsToRequest(sdk: Int): List<String> = when {
        sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )

        sdk >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )

        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
    }

    fun level(sdk: Int, isGranted: (String) -> Boolean): MediaAccessLevel = when {
        sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
            isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccessLevel.FULL

            isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccessLevel.PARTIAL

            else -> MediaAccessLevel.NONE
        }

        sdk >= Build.VERSION_CODES.TIRAMISU ->
            if (isGranted(Manifest.permission.READ_MEDIA_IMAGES)) MediaAccessLevel.FULL else MediaAccessLevel.NONE

        else ->
            if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccessLevel.FULL else MediaAccessLevel.NONE
    }
}

@Singleton
class MediaAccess @Inject constructor(@ApplicationContext private val context: Context) {
    val permissionsToRequest: Array<String>
        get() = MediaAccessRules.permissionsToRequest(Build.VERSION.SDK_INT).toTypedArray()

    fun level(): MediaAccessLevel = MediaAccessRules.level(Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
