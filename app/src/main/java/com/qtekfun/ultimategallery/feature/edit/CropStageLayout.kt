package com.qtekfun.ultimategallery.feature.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * How the photo is laid out on the stage: its angle, mirror, straighten angle and the number of stage pixels per photo
 * pixel. The screen turns these values into the chained layers of the photo, and [CropStageLayout.photoCorners] applies
 * the same chain, so tests can check where the photo lands.
 */
data class PhotoPose(val rotationDeg: Float, val flipX: Float, val flipY: Float, val straightenDeg: Float, val scale: Float)

/** Pure layout math of the crop stage: where the photo and the crop frame land on screen. */
object CropStageLayout {
    private const val QUARTER = 90f

    /** Stage pixels per photo pixel that make the photo, turned by [rotationDeg], fit a [stageW] x [stageH] stage. */
    fun fitScale(stageW: Float, stageH: Float, photoW: Float, photoH: Float, rotationDeg: Float): Float {
        val radians = Math.toRadians(rotationDeg.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        return min(stageW / (photoW * c + photoH * s), stageH / (photoW * s + photoH * c))
    }

    /** Stage pixels per canvas pixel that make the canvas fit the stage; the frame is drawn at this scale. */
    fun frameScale(stageW: Float, stageH: Float, canvas: CanvasSize): Float = min(stageW / canvas.width, stageH / canvas.height)

    /** The pose of the photo once everything has settled: turned, mirrored and straightened as [spec] says. */
    fun restPose(stageW: Float, stageH: Float, photoW: Float, photoH: Float, spec: CropRotateSpec): PhotoPose {
        val rotation = spec.quarterTurns * QUARTER
        val fit = fitScale(stageW, stageH, photoW, photoH, rotation)
        return PhotoPose(rotation, if (spec.flipH) -1f else 1f, if (spec.flipV) -1f else 1f, spec.straightenDeg, fit)
    }

    /**
     * Where the corners of the photo (top left, top right, bottom right, bottom left) land on a [stageW] x [stageH]
     * stage. The photo is turned, then mirrored, then straightened and scaled, all around the stage center, which is the
     * order of the layers of the screen and of the saved result.
     */
    fun photoCorners(stageW: Float, stageH: Float, photoW: Float, photoH: Float, pose: PhotoPose): List<Offset> {
        val hw = photoW / 2f
        val hh = photoH / 2f
        return listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(hw, hh), Offset(-hw, hh)).map { p ->
            val turned = rotate(p, pose.rotationDeg)
            val mirrored = Offset(turned.x * pose.flipX, turned.y * pose.flipY)
            val straightened = rotate(mirrored, pose.straightenDeg)
            Offset(stageW / 2f + straightened.x * pose.scale, stageH / 2f + straightened.y * pose.scale)
        }
    }

    /** The crop frame on a stage of [stageW] x [stageH] when it is at rest: [crop] of the centered canvas. */
    fun frameRect(stageW: Float, stageH: Float, canvas: CanvasSize, crop: CropRect): Rect {
        val k = frameScale(stageW, stageH, canvas)
        val left = (stageW - canvas.width * k) / 2f
        val top = (stageH - canvas.height * k) / 2f
        return Rect(
            left + crop.left * canvas.width * k,
            top + crop.top * canvas.height * k,
            left + crop.right * canvas.width * k,
            top + crop.bottom * canvas.height * k
        )
    }

    private fun rotate(p: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
    }
}
