package com.qtekfun.ultimategallery.data.video

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Minimal random access to a file's bytes, so the MP4 code runs on files, descriptors and byte arrays alike. */
interface RandomAccess {
    val size: Long

    /** Reads up to [len] bytes at [pos] into [buf] at [off]; returns the count read, or -1 at the end. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int

    /** Overwrites the bytes starting at [pos]; never extends the file. */
    fun write(pos: Long, bytes: ByteArray)
}

/** [RandomAccess] over file channels (positional reads and writes, the file position is never used). */
class FileChannelRandomAccess(private val readChannel: FileChannel, private val writeChannel: FileChannel = readChannel) : RandomAccess {
    constructor(file: RandomAccessFile) : this(file.channel)

    override val size: Long get() = readChannel.size()

    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int = readChannel.read(ByteBuffer.wrap(buf, off, len), pos)

    override fun write(pos: Long, bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        var at = pos
        while (buffer.hasRemaining()) at += writeChannel.write(buffer, at)
    }
}

/** [RandomAccess] over a byte array (tests). */
class ByteArrayRandomAccess(val bytes: ByteArray) : RandomAccess {
    override val size: Long get() = bytes.size.toLong()

    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= bytes.size) return -1
        val count = minOf(len.toLong(), bytes.size - pos).toInt()
        System.arraycopy(bytes, pos.toInt(), buf, off, count)
        return count
    }

    override fun write(pos: Long, bytes: ByteArray) {
        require(pos >= 0 && pos + bytes.size <= this.bytes.size) { "Write outside the array" }
        System.arraycopy(bytes, 0, this.bytes, pos.toInt(), bytes.size)
    }
}

/**
 * Reads and rewrites the rotation flag of MP4, MOV and 3GP files: the 3x3 matrix in the `tkhd` box of
 * every video track. Only box headers are read, by seeking, so a `moov` that follows a multi-gigabyte
 * `mdat` costs a handful of small reads. Rotations are degrees clockwise (0, 90, 180, 270).
 */
@Suppress("TooManyFunctions")
object Mp4Rotation {
    private const val ONE = 0x10000
    private const val MATRIX_BYTES = 36
    private const val MAX_DEPTH_BOXES = 1_000_000

    private val MOOV = fourcc("moov")
    private val TRAK = fourcc("trak")
    private val MDIA = fourcc("mdia")
    private val TKHD = fourcc("tkhd")
    private val HDLR = fourcc("hdlr")
    private val VIDE = fourcc("vide")

    private class Box(val type: Int, val payload: Long, val end: Long)

    /** Matrix values a, b, c, d (16.16) for each rotation. */
    private val rotations = mapOf(
        0 to intArrayOf(ONE, 0, 0, ONE),
        90 to intArrayOf(0, ONE, -ONE, 0),
        180 to intArrayOf(-ONE, 0, 0, -ONE),
        270 to intArrayOf(0, -ONE, ONE, 0)
    )

    /** The rotation of the video track, or null when the file is not an MP4-style file, has no video track or is mirrored or skewed. */
    fun currentRotation(file: RandomAccess): Int? = readRotations(file)?.firstOrNull()

    /**
     * Adds [quarterTurnsClockwise] (any sign or size) to the rotation of the video track(s) and writes the
     * result into every video track header. Returns the new rotation, or null (and writes nothing) when unsupported.
     */
    fun rotate(file: RandomAccess, quarterTurnsClockwise: Int): Int? {
        val offsets = findMatrixOffsets(file) ?: return null
        val current = rotationOf(file, offsets.first()) ?: return null
        val target = Math.floorMod(current / 90 + quarterTurnsClockwise, 4) * 90
        val matrix = matrixBytes(target)
        // Every track is validated before the first byte is written.
        if (offsets.any { rotationOf(file, it) == null }) return null
        offsets.forEach { file.write(it, matrix) }
        return target
    }

    private fun readRotations(file: RandomAccess): List<Int>? {
        val offsets = findMatrixOffsets(file) ?: return null
        val all = offsets.map { rotationOf(file, it) ?: return null }
        return all
    }

    private fun matrixBytes(degrees: Int): ByteArray {
        val abcd = requireNotNull(rotations[degrees])
        val values = intArrayOf(abcd[0], abcd[1], 0, abcd[2], abcd[3], 0, 0, 0, 1 shl 30)
        val buffer = ByteBuffer.allocate(MATRIX_BYTES)
        values.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    private fun rotationOf(file: RandomAccess, matrixOffset: Long): Int? {
        val raw = ByteArray(MATRIX_BYTES)
        if (!readFully(file, matrixOffset, raw)) return null
        val buffer = ByteBuffer.wrap(raw)
        val m = IntArray(9) { buffer.getInt() }
        val abcd = intArrayOf(m[0], m[1], m[3], m[4])
        return rotations.entries.firstOrNull { it.value.contentEquals(abcd) }?.key
    }

    /** File offsets of the matrices of all video tracks, or null when the file is not a usable MP4 or has no video track. */
    private fun findMatrixOffsets(file: RandomAccess): List<Long>? {
        val moov = findTopLevelMoov(file) ?: return null
        val offsets = mutableListOf<Long>()
        forEachBox(file, moov.payload, moov.end) { trak ->
            if (trak.type == TRAK) trackMatrixOffset(file, trak)?.let { offsets += it }
            true
        }
        return offsets.ifEmpty { null }
    }

    private fun findTopLevelMoov(file: RandomAccess): Box? {
        val header = ByteArray(8)
        if (!readFully(file, 0, header) || !looksLikeBoxType(header, 4)) return null
        var moov: Box? = null
        forEachBox(file, 0, file.size) { box ->
            if (box.type == MOOV) {
                moov = box
                false
            } else {
                true
            }
        }
        return moov
    }

    private fun trackMatrixOffset(file: RandomAccess, trak: Box): Long? {
        var isVideo = false
        var matrix: Long? = null
        forEachBox(file, trak.payload, trak.end) { box ->
            when (box.type) {
                TKHD -> matrix = tkhdMatrixOffset(file, box)
                MDIA -> isVideo = handlerIsVideo(file, box)
            }
            true
        }
        return if (isVideo) matrix else null
    }

    private fun tkhdMatrixOffset(file: RandomAccess, tkhd: Box): Long? {
        val version = ByteArray(1)
        if (!readFully(file, tkhd.payload, version)) return null
        val offset = tkhd.payload + if (version[0].toInt() == 1) 52 else 40
        return if (offset + MATRIX_BYTES <= tkhd.end) offset else null
    }

    private fun handlerIsVideo(file: RandomAccess, mdia: Box): Boolean {
        var video = false
        forEachBox(file, mdia.payload, mdia.end) { box ->
            if (box.type == HDLR && box.payload + 12 <= box.end) {
                val type = ByteArray(4)
                video = readFully(file, box.payload + 8, type) && ByteBuffer.wrap(type).getInt() == VIDE
            }
            true
        }
        return video
    }

    /** Calls [block] for each box in [start, end) until it returns false; stops quietly at the first malformed header. */
    private fun forEachBox(file: RandomAccess, start: Long, end: Long, block: (Box) -> Boolean) {
        var pos = start
        var count = 0
        val header = ByteArray(16)
        while (pos + 8 <= end && count++ < MAX_DEPTH_BOXES) {
            if (readAtMost(file, pos, header) < 8) return
            val buffer = ByteBuffer.wrap(header)
            val size32 = buffer.getInt().toLong() and 0xFFFFFFFFL
            val type = buffer.getInt()
            var headerSize = 8
            var size = size32
            if (size32 == 1L) {
                if (pos + 16 > end) return
                headerSize = 16
                size = buffer.getLong()
            } else if (size32 == 0L) {
                size = end - pos
            }
            if (size < headerSize) return
            val boxEnd = if (size > end - pos) end else pos + size
            if (!block(Box(type, pos + headerSize, boxEnd))) return
            pos = boxEnd
        }
    }

    private fun looksLikeBoxType(bytes: ByteArray, from: Int): Boolean = (from until from + 4).all {
        val b = bytes[it].toInt() and 0xFF
        b in 0x20..0x7E || b == 0xA9
    }

    private fun readAtMost(file: RandomAccess, pos: Long, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = file.read(pos + total, buf, total, buf.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun readFully(file: RandomAccess, pos: Long, buf: ByteArray): Boolean = readAtMost(file, pos, buf) == buf.size

    private fun fourcc(s: String): Int = ByteBuffer.wrap(s.toByteArray(Charsets.ISO_8859_1)).getInt()
}
