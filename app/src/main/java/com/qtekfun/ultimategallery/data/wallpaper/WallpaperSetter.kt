package com.qtekfun.ultimategallery.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import com.qtekfun.ultimategallery.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Which screens get the wallpaper. */
enum class WallpaperTarget(val flags: Int) {
    HOME(WallpaperManager.FLAG_SYSTEM),
    LOCK(WallpaperManager.FLAG_LOCK),
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
}

/** Sets a photo, cropped to the screen, as the system wallpaper. */
class WallpaperSetter @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) {
    private val manager: WallpaperManager get() = WallpaperManager.getInstance(context)

    /** False on devices without wallpapers or where a device policy forbids changing it. */
    val isAvailable: Boolean get() = manager.isWallpaperSupported && manager.isSetWallpaperAllowed

    /**
     * Decodes the [crop] of the photo at [uri] at the size of a [screenWidth] x [screenHeight] screen
     * and sets it on [target]. Returns false when the photo could not be decoded or the system refused.
     */
    suspend fun set(uri: Uri, crop: CropFraction, screenWidth: Int, screenHeight: Int, target: WallpaperTarget): Boolean = withContext(io) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val plan = WallpaperGeometry.decodePlan(info.size.width, info.size.height, crop, screenWidth, screenHeight)
                decoder.setTargetSize(plan.targetWidth, plan.targetHeight)
                decoder.crop = Rect(plan.left, plan.top, plan.right, plan.bottom)
            }
            try {
                manager.setBitmap(bitmap, null, true, target.flags) != 0
            } finally {
                bitmap.recycle()
            }
        }.getOrDefault(false)
    }
}
