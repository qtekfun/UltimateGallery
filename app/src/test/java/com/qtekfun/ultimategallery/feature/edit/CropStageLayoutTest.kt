package com.qtekfun.ultimategallery.feature.edit

import androidx.compose.ui.geometry.Offset
import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropStageLayoutTest {
    private val stages = listOf(300f to 500f, 520f to 340f)
    private val sources = listOf(400 to 300, 300 to 400, 1000 to 1000)
    private val angles = listOf(0f, 3.5f, -12f, 45f, -45f)

    private fun cross(a: Offset, b: Offset, p: Offset) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    /** True when [p] is inside or on the border of the convex quad, within [tolerance] pixels. */
    private fun inside(quad: List<Offset>, p: Offset, tolerance: Float): Boolean {
        val signs = quad.indices.map { i ->
            val a = quad[i]
            val b = quad[(i + 1) % quad.size]
            val len = (b - a).getDistance()
            cross(a, b, p) / len
        }
        return signs.all { it >= -tolerance } || signs.all { it <= tolerance }
    }

    private fun specs(src: Pair<Int, Int>): List<CropRotateSpec> = (-4..4).flatMap { turns ->
        val canvas = CropGeometry.canvasSize(src.first.toFloat(), src.second.toFloat(), turns.mod(4))
        listOf(false, true).flatMap { flipH ->
            listOf(false, true).flatMap { flipV ->
                angles.flatMap { angle ->
                    listOf(null, 16f / 9f).map { aspect ->
                        CropRotateSpec(turns, flipH, flipV, angle, CropGeometry.largestInscribed(canvas, angle, aspect))
                    }
                }
            }
        }
    }

    private fun forEachCombination(block: (stage: Pair<Float, Float>, src: Pair<Int, Int>, spec: CropRotateSpec) -> Unit) {
        for (stage in stages) for (src in sources) specs(src).forEach { block(stage, src, it) }
    }

    @Test
    fun theFrameLiesInsideThePhotoForEveryCombination() {
        forEachCombination { (sw, sh), (pw, ph), spec ->
            val pose = CropStageLayout.restPose(sw, sh, pw.toFloat(), ph.toFloat(), spec)
            val quad = CropStageLayout.photoCorners(sw, sh, pw.toFloat(), ph.toFloat(), pose)
            val canvas = CropGeometry.canvasSize(pw.toFloat(), ph.toFloat(), spec.turns)
            val frame = CropStageLayout.frameRect(sw, sh, canvas, spec.crop)
            val corners =
                listOf(Offset(frame.left, frame.top), Offset(frame.right, frame.top), Offset(frame.right, frame.bottom), Offset(frame.left, frame.bottom))
            corners.forEach { assertTrue("$spec on $pw x $ph, stage $sw x $sh: $it outside $quad", inside(quad, it, TOLERANCE)) }
        }
    }

    @Test
    fun theUncroppedFrameCoincidesWithTheUnstraightenedPhoto() {
        forEachCombination { (sw, sh), (pw, ph), generated ->
            val spec = generated.copy(straightenDeg = 0f, crop = CropRect.FULL)
            val pose = CropStageLayout.restPose(sw, sh, pw.toFloat(), ph.toFloat(), spec)
            val quad = CropStageLayout.photoCorners(sw, sh, pw.toFloat(), ph.toFloat(), pose)
            val frame = CropStageLayout.frameRect(sw, sh, CropGeometry.canvasSize(pw.toFloat(), ph.toFloat(), spec.turns), CropRect.FULL)
            assertEquals(frame.left, quad.minOf { it.x }, 0.01f)
            assertEquals(frame.right, quad.maxOf { it.x }, 0.01f)
            assertEquals(frame.top, quad.minOf { it.y }, 0.01f)
            assertEquals(frame.bottom, quad.maxOf { it.y }, 0.01f)
        }
    }

    @Test
    fun thePreviewAgreesWithTheSavedPhotoForEveryCombination() {
        forEachCombination { (sw, sh), (pw, ph), spec ->
            val pose = CropStageLayout.restPose(sw, sh, pw.toFloat(), ph.toFloat(), spec)
            val quad = CropStageLayout.photoCorners(sw, sh, pw.toFloat(), ph.toFloat(), pose)
            val canvas = CropGeometry.canvasSize(pw.toFloat(), ph.toFloat(), spec.turns)
            val frame = CropStageLayout.frameRect(sw, sh, canvas, spec.crop)
            val k = CropStageLayout.frameScale(sw, sh, canvas)
            val m = CropGeometry.compositeAffine(pw, ph, spec)
            val sources = listOf(0.0 to 0.0, pw.toDouble() to 0.0, pw.toDouble() to ph.toDouble(), 0.0 to ph.toDouble())
            sources.forEachIndexed { i, (x, y) ->
                val outX = m[0] * x + m[2] * y + m[4]
                val outY = m[1] * x + m[3] * y + m[5]
                val expectedX = frame.left + outX.toFloat() * k
                val expectedY = frame.top + outY.toFloat() * k
                assertTrue("$spec corner $i: ${quad[i]} vs ($expectedX, $expectedY)", abs(quad[i].x - expectedX) < 0.05f && abs(quad[i].y - expectedY) < 0.05f)
            }
        }
    }

    @Test
    fun theFitScaleKeepsTheTurningPhotoInsideTheStage() {
        for ((sw, sh) in stages) {
            for ((pw, ph) in sources) {
                for (deg in 0..360 step 15) {
                    val pose = PhotoPose(deg.toFloat(), 1f, 1f, 0f, CropStageLayout.fitScale(sw, sh, pw.toFloat(), ph.toFloat(), deg.toFloat()))
                    val quad = CropStageLayout.photoCorners(sw, sh, pw.toFloat(), ph.toFloat(), pose)
                    assertTrue(quad.all { it.x >= -0.05f && it.x <= sw + 0.05f && it.y >= -0.05f && it.y <= sh + 0.05f })
                }
            }
        }
    }

    @Test
    fun aStaleStoredSizeYieldsToTheDecodedShape() {
        // Stored landscape, decoded portrait (a photo whose orientation was not applied to the stored size).
        val size = CropGeometry.reconcileSize(4000, 3000, CanvasSize(1500f, 2000f))
        assertEquals(3000f / 4000f, size.width / size.height, 1e-4f)
        assertEquals(4000f, size.height, 1e-3f)
    }

    @Test
    fun aDownsampledDecodeKeepsTheStoredSize() {
        assertEquals(CanvasSize(4000f, 3000f), CropGeometry.reconcileSize(4000, 3000, CanvasSize(1024f, 768f)))
        assertEquals(CanvasSize(4000f, 3000f), CropGeometry.reconcileSize(4000, 3000, null))
    }

    @Test
    fun missingStoredSizeFallsBackToTheDecodedOne() {
        assertEquals(CanvasSize(640f, 480f), CropGeometry.reconcileSize(0, 0, CanvasSize(640f, 480f)))
        assertEquals(CanvasSize(1f, 1f), CropGeometry.reconcileSize(0, 0, null))
    }

    private companion object {
        const val TOLERANCE = 0.05f
    }
}
