package com.qtekfun.ultimategallery.data.video

import com.qtekfun.ultimategallery.data.video.Mp4Builder.concat
import com.qtekfun.ultimategallery.data.video.Mp4Builder.identity
import com.qtekfun.ultimategallery.data.video.Mp4Builder.matrix
import com.qtekfun.ultimategallery.data.video.Mp4Builder.matrixOf
import com.qtekfun.ultimategallery.data.video.Mp4Builder.rotated180
import com.qtekfun.ultimategallery.data.video.Mp4Builder.rotated270
import com.qtekfun.ultimategallery.data.video.Mp4Builder.rotated90
import com.qtekfun.ultimategallery.data.video.Mp4Builder.trak
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp4RotationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun rotation(bytes: ByteArray) = Mp4Rotation.currentRotation(ByteArrayRandomAccess(bytes))

    private fun rotate(bytes: ByteArray, turns: Int) = Mp4Rotation.rotate(ByteArrayRandomAccess(bytes), turns)

    @Test
    fun readsAllFourRotationsFromBothTkhdVersions() {
        for (version in listOf(0, 1)) {
            assertEquals(0, rotation(Mp4Builder.mp4(trak("vide", identity, version))))
            assertEquals(90, rotation(Mp4Builder.mp4(trak("vide", rotated90, version))))
            assertEquals(180, rotation(Mp4Builder.mp4(trak("vide", rotated180, version))))
            assertEquals(270, rotation(Mp4Builder.mp4(trak("vide", rotated270, version))))
        }
    }

    @Test
    fun rotateWritesTheStandardMatrices() {
        for (version in listOf(0, 1)) {
            val file = Mp4Builder.mp4(trak("vide", identity, version))
            assertEquals(90, rotate(file, 1))
            assertArrayEquals(rotated90, matrixOf(file))
            assertEquals(180, rotate(file, 1))
            assertArrayEquals(rotated180, matrixOf(file))
            assertEquals(90, rotate(file, -1))
            assertEquals(0, rotate(file, -1))
            assertArrayEquals(identity, matrixOf(file))
        }
    }

    @Test
    fun onlyTheMatrixBytesChangeAndLengthIsKept() {
        val original = Mp4Builder.mp4(trak("vide", identity))
        val patched = original.copyOf()
        rotate(patched, 1)
        assertEquals(original.size, patched.size)
        val changed = original.indices.filter { original[it] != patched[it] }
        val start = changed.first()
        assertTrue("changes span more than the matrix", changed.last() - start < 36)
    }

    @Test
    fun fourQuarterTurnsComposeBackToZeroAndBigTurnsWrap() {
        val file = Mp4Builder.mp4(trak("vide", identity))
        repeat(3) { rotate(file, 1) }
        assertEquals(270, rotation(file))
        assertEquals(0, rotate(file, 1))
        assertEquals(90, rotate(file, 5))
        assertEquals(270, rotate(file, -6))
        assertEquals(270, rotate(file, 4))
    }

    @Test
    fun audioTracksAreIgnoredAndVideoTrackAnywhereIsFound() {
        val audio = trak("soun", identity)
        val file = Mp4Builder.mp4(audio, trak("vide", rotated90), audio)
        assertEquals(90, rotation(file))
        rotate(file, 1)
        assertEquals(180, rotation(file))
        assertArrayEquals(identity, matrixOf(file, 0))
        assertArrayEquals(rotated180, matrixOf(file, 1))
        assertArrayEquals(identity, matrixOf(file, 2))
    }

    @Test
    fun allVideoTracksArePatched() {
        val file = Mp4Builder.mp4(trak("vide", rotated90), trak("vide", rotated90, version = 1))
        assertEquals(180, rotate(file, 1))
        assertArrayEquals(rotated180, matrixOf(file, 0))
        assertArrayEquals(rotated180, matrixOf(file, 1))
    }

    @Test
    fun audioOnlyFileIsUnsupported() {
        val file = Mp4Builder.mp4(trak("soun", identity))
        assertNull(rotation(file))
        assertNull(rotate(file, 1))
    }

    @Test
    fun moovBeforeMdatWorks() {
        val file = concat(Mp4Builder.ftyp(), Mp4Builder.moov(trak("vide", rotated270)), Mp4Builder.mdat(300))
        assertEquals(270, rotation(file))
        assertEquals(0, rotate(file, 1))
    }

    @Test
    fun sixtyFourBitMdatSizeIsHandled() {
        val mdat = Mp4Builder.largeBox("mdat", ByteArray(400))
        val file = concat(Mp4Builder.ftyp("mp42"), mdat, Mp4Builder.moov(trak("vide", rotated90)))
        assertEquals(90, rotation(file))
        assertEquals(180, rotate(file, 1))
    }

    @Test
    fun moovThatRunsToTheEndOfFileWithSizeZeroIsHandled() {
        val moov = Mp4Builder.moov(trak("vide", rotated90))
        val payload = moov.copyOfRange(8, moov.size)
        val file = concat(Mp4Builder.ftyp("qt  "), Mp4Builder.mdat(100), Mp4Builder.toEndBox("moov", payload))
        assertEquals(90, rotation(file))
    }

    @Test
    fun hugeMdatIsSkippedWithoutReadingIt() {
        val moov = Mp4Builder.moov(trak("vide", rotated90))
        val ftyp = Mp4Builder.ftyp()
        val big = 3L * 1024 * 1024 * 1024
        val mdatHeader = java.nio.ByteBuffer.allocate(16).putInt(1).put("mdat".toByteArray()).putLong(16 + big).array()
        val access = object : RandomAccess {
            private val head = concat(ftyp, mdatHeader)
            private val moovAt = head.size + big
            override val size: Long = moovAt + moov.size
            var bytesRead = 0L

            override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
                var n = 0
                while (n < len && pos + n < size) {
                    val p = pos + n
                    buf[off + n] = when {
                        p < head.size -> head[p.toInt()]
                        p < moovAt -> 0
                        else -> moov[(p - moovAt).toInt()]
                    }
                    n++
                }
                bytesRead += n
                return if (n == 0) -1 else n
            }

            override fun write(pos: Long, bytes: ByteArray) = throw UnsupportedOperationException()
        }
        assertEquals(90, Mp4Rotation.currentRotation(access))
        assertTrue("read ${access.bytesRead} bytes", access.bytesRead < 4096)
    }

    @Test
    fun unknownBrandWithMoovIsAccepted() {
        assertEquals(90, rotation(Mp4Builder.mp4(trak("vide", rotated90), brand = "zzzz")))
        assertEquals(0, rotation(Mp4Builder.mp4(trak("vide", identity), brand = "3gp5")))
    }

    @Test
    fun nonMp4InputIsUnsupported() {
        val matroska = intArrayOf(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 1, 0x42, 0xF7, 0x81, 1).map { it.toByte() }.toByteArray()
        assertNull(rotation(matroska))
        assertNull(rotate(matroska, 1))
        assertNull(rotation(ByteArray(0)))
        assertNull(rotation(ByteArray(5) { 7 }))
        assertNull(rotation(ByteArray(2000) { (it * 7 + 3).toByte() }))
        assertNull(rotation(concat(Mp4Builder.ftyp(), Mp4Builder.mdat(100))))
    }

    @Test
    fun mirroredAndSkewedMatricesAreUnsupportedAndLeftUntouched() {
        val mirrored = Mp4Builder.mp4(trak("vide", matrix(-Mp4Builder.ONE, 0, 0, Mp4Builder.ONE)))
        val before = mirrored.copyOf()
        assertNull(rotation(mirrored))
        assertNull(rotate(mirrored, 1))
        assertArrayEquals(before, mirrored)
        assertNull(rotation(Mp4Builder.mp4(trak("vide", matrix(46341, 46341, -46341, 46341)))))
    }

    @Test
    fun truncatedFileDoesNotCrash() {
        val file = Mp4Builder.mp4(trak("vide", rotated90))
        for (cut in listOf(0, 3, 9, 20, file.size - 40, file.size - 1)) {
            rotation(file.copyOf(cut))
        }
    }

    @Test
    fun worksThroughAFileChannel() {
        val bytes = Mp4Builder.mp4(trak("vide", rotated90))
        val target = File(folder.root, "clip.mp4").apply { writeBytes(bytes) }
        RandomAccessFile(target, "rw").use { raf ->
            val access = FileChannelRandomAccess(raf)
            assertEquals(90, Mp4Rotation.currentRotation(access))
            assertEquals(180, Mp4Rotation.rotate(access, 1))
        }
        val after = target.readBytes()
        assertEquals(bytes.size, after.size)
        assertArrayEquals(rotated180, matrixOf(after))
    }
}
