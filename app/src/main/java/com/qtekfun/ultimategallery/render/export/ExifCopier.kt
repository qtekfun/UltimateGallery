package com.qtekfun.ultimategallery.render.export

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Writes the EXIF block of an exported file. The pixels of an export are already rotated, so the
 * orientation is always normal and the pixel dimensions always describe the output.
 */
class ExifCopier @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Applies [mode] to [output] (an encoded JPEG, PNG or WebP file) using the metadata of [original].
     * Returns false when the metadata could not be written; never throws.
     */
    fun apply(output: File, original: Uri, mode: ExifMode, width: Int, height: Int): Boolean = try {
        if (mode == ExifMode.STRIP_ALL) {
            true
        } else {
            val target = ExifInterface(output)
            copyFrom(original, target, withLocation = mode == ExifMode.KEEP)
            if (mode == ExifMode.STRIP_LOCATION) GPS_TAGS.forEach { target.setAttribute(it, null) }
            target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            target.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            target.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            target.saveAttributes()
            true
        }
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun copyFrom(original: Uri, target: ExifInterface, withLocation: Boolean) {
        val source = openOriginal(original) ?: return
        source.use { stream ->
            val exif = ExifInterface(stream)
            val tags = if (withLocation) COPIED_TAGS + GPS_TAGS else COPIED_TAGS
            tags.forEach { tag -> exif.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
        }
    }

    /** Opens the unredacted original when possible so the GPS block is not stripped by the system. */
    private fun openOriginal(uri: Uri): InputStream? {
        val resolver = context.contentResolver
        val candidates = buildList {
            if (uri.scheme == "content") {
                try {
                    add(MediaStore.setRequireOriginal(uri))
                } catch (_: UnsupportedOperationException) {
                    // Not a MediaStore uri: use it as it is.
                } catch (_: SecurityException) {
                    // ACCESS_MEDIA_LOCATION missing: the redacted copy is the best we can get.
                }
            }
            add(uri)
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            try {
                resolver.openInputStream(candidate)
            } catch (_: SecurityException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            } catch (_: IOException) {
                null
            }
        }
    }

    companion object {
        /** Every GPS tag; the location of the photo. */
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR
        )

        /** Camera, exposure and date tags that are safe to carry over. Orientation and size are not among them. */
        val COPIED_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_SENSITIVITY_TYPE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_MAX_APERTURE_VALUE,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_COLOR_SPACE,
            ExifInterface.TAG_X_RESOLUTION,
            ExifInterface.TAG_Y_RESOLUTION,
            ExifInterface.TAG_RESOLUTION_UNIT
        )
    }
}
