package com.qtekfun.ultimategallery.feature.viewer

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the info panel shows about a photo or video. Null fields are not shown. */
data class MediaDetails(
    val name: String,
    val path: String?,
    val dateMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val durationMs: Long?,
    val camera: String?,
    val lens: String?,
    val aperture: String?,
    val exposure: String?,
    val iso: String?,
    val focalLength: String?,
    val latitude: Double?,
    val longitude: Double?
) {
    val megapixels: Double get() = width.toLong() * height / MEGA
    val hasLocation: Boolean get() = latitude != null && longitude != null

    companion object {
        private const val MEGA = 1_000_000.0
    }
}

/** Pure formatting helpers for the info panel. */
object DetailsFormat {
    private const val KIB = 1024.0

    fun size(bytes: Long): String {
        if (bytes < KIB) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= KIB && unit < units.lastIndex) {
            value /= KIB
            unit++
        }
        return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
    }

    /** `1/125 s` for fast exposures, `2.5 s` for slow ones. */
    fun exposure(seconds: Double): String = when {
        seconds <= 0.0 -> ""
        seconds < 1.0 -> "1/${(1.0 / seconds).roundToInt()} s"
        else -> String.format(Locale.US, "%.1f s", seconds)
    }

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f° %s, %.5f° %s", abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}

/** Reads the EXIF block of a photo. Location needs the original file, which requires ACCESS_MEDIA_LOCATION. */
@Singleton
class MediaDetailsReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(item: MediaItem): MediaDetails {
        val exif = if (item.isVideo) null else openExif(item.uri)
        val make = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim()
        val model = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim()
        val latLong = exif?.latLong
        return MediaDetails(
            name = item.displayName,
            path = item.relativePath,
            dateMs = item.dateMs,
            sizeBytes = item.sizeBytes,
            width = item.width,
            height = item.height,
            mimeType = item.mimeType,
            durationMs = item.durationMs.takeIf { item.isVideo },
            camera = listOfNotNull(make, model).filter { it.isNotEmpty() }.distinct().joinToString(" ").ifEmpty { null },
            lens = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null },
            aperture = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)?.takeIf { it > 0 }?.let { String.format(Locale.US, "f/%.1f", it) },
            exposure = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)?.takeIf { it > 0 }?.let(DetailsFormat::exposure),
            iso = exif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
            focalLength = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f mm", it) },
            latitude = latLong?.get(0),
            longitude = latLong?.get(1)
        )
    }

    private fun openExif(uri: Uri): ExifInterface? {
        // Asking for the original gets the unredacted location; fall back to the redacted copy.
        val candidates = listOf(runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull(), uri)
        for (candidate in candidates.filterNotNull()) {
            val exif = runCatching {
                context.contentResolver.openInputStream(candidate)?.use { ExifInterface(it) }
            }.getOrNull()
            if (exif != null) return exif
        }
        return null
    }
}
