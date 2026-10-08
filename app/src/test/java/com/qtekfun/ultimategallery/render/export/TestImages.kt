package com.qtekfun.ultimategallery.render.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs

/** Helpers that write small images for the export tests. */
object TestImages {
    fun solid(width: Int, height: Int, color: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** Left half [left], right half [right]. */
    fun halves(width: Int, height: Int, left: Int, right: Int): Bitmap {
        val bitmap = solid(width, height, right)
        Canvas(bitmap).drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = left })
        return bitmap
    }

    fun write(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
        file.outputStream().use { bitmap.compress(format, 95, it) }
        return file
    }

    fun writeJpeg(bitmap: Bitmap, file: File, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        write(bitmap, file, Bitmap.CompressFormat.JPEG)
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            val exif = ExifInterface(file)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            exif.saveAttributes()
        }
        return file
    }

    /** True when every channel of [actual] is within [tolerance] of [expected] (JPEG is lossy). */
    fun near(expected: Int, actual: Int, tolerance: Int = 24): Boolean = abs(Color.red(expected) - Color.red(actual)) <= tolerance &&
        abs(Color.green(expected) - Color.green(actual)) <= tolerance &&
        abs(Color.blue(expected) - Color.blue(actual)) <= tolerance
}
