package com.qtekfun.ultimategallery.data.edit

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.domain.MediaItem
import java.io.File
import java.io.InputStream

/** Colors of the four quadrants of [quadrants]. */
object Quadrant {
    const val TOP_LEFT = Color.RED
    const val TOP_RIGHT = Color.GREEN
    const val BOTTOM_LEFT = Color.BLUE
    const val BOTTOM_RIGHT = Color.YELLOW
}

/** A photo whose four quadrants have distinct colors, so any turn, mirror or cut can be told apart. */
fun quadrants(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val halfW = width / 2f
    val halfH = height / 2f
    paint.color = Quadrant.TOP_LEFT
    canvas.drawRect(0f, 0f, halfW, halfH, paint)
    paint.color = Quadrant.TOP_RIGHT
    canvas.drawRect(halfW, 0f, width.toFloat(), halfH, paint)
    paint.color = Quadrant.BOTTOM_LEFT
    canvas.drawRect(0f, halfH, halfW, height.toFloat(), paint)
    paint.color = Quadrant.BOTTOM_RIGHT
    canvas.drawRect(halfW, halfH, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

/** The colors at the centers of the four quadrants of [bitmap]: top left, top right, bottom left, bottom right. */
fun quadrantColors(bitmap: Bitmap): List<Int> {
    val w = bitmap.width
    val h = bitmap.height
    return listOf(
        bitmap.getPixel(w / 4, h / 4),
        bitmap.getPixel(3 * w / 4, h / 4),
        bitmap.getPixel(w / 4, 3 * h / 4),
        bitmap.getPixel(3 * w / 4, 3 * h / 4)
    )
}

fun mediaItem(file: File, mimeType: String, width: Int, height: Int, name: String = file.name) = MediaItem(
    id = 7,
    uri = Uri.fromFile(file),
    displayName = name,
    mimeType = mimeType,
    isVideo = false,
    dateMs = 1_700_000_000_000L,
    width = width,
    height = height,
    sizeBytes = file.length(),
    durationMs = 0,
    bucketId = 1,
    relativePath = "DCIM/Camera/"
)

fun writeJpeg(bitmap: Bitmap, file: File, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    if (orientation != ExifInterface.ORIENTATION_NORMAL) {
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
    }
    return file
}

fun writePng(bitmap: Bitmap, file: File): File {
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** An [EditStore] on plain files, recording what was written. */
class FakeEditStore(private val context: Context) : EditStore {
    class Inserted(val bytes: ByteArray, val displayName: String, val mimeType: String, val relativePath: String?, val dateTakenMs: Long)

    val inserted = mutableListOf<Inserted>()
    val overwritten = mutableMapOf<Uri, ByteArray>()
    var exifUpdates = 0
    var writeRequests = 0

    override fun openOriginal(uri: Uri): InputStream? = File(requireNotNull(uri.path)).inputStream()

    override fun insertCopy(file: File, displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri {
        inserted += Inserted(file.readBytes(), displayName, mimeType, relativePath, dateTakenMs)
        return Uri.parse("content://media/external/images/media/${100 + inserted.size}")
    }

    override fun overwrite(uri: Uri, file: File) {
        overwritten[uri] = file.readBytes()
    }

    override fun updateExifInPlace(uri: Uri, block: (ExifInterface) -> Unit) {
        exifUpdates++
        block(ExifInterface(File(requireNotNull(uri.path))))
    }

    override fun createWriteRequest(uri: Uri): IntentSender {
        writeRequests++
        return PendingIntent.getBroadcast(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE).intentSender
    }
}
