package com.qtekfun.ultimategallery.data.video

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Builds small synthetic ISO base media files for the tests. */
object Mp4Builder {
    const val ONE = 0x10000

    fun box(type: String, payload: ByteArray = ByteArray(0)): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray()).put(payload).array()

    /** A box with a 64-bit `largesize` header. */
    fun largeBox(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(16 + payload.size).putInt(1).put(type.toByteArray()).putLong(16L + payload.size).put(payload).array()

    /** A box whose size field is 0: it runs to the end of the file. */
    fun toEndBox(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(8 + payload.size).putInt(0).put(type.toByteArray()).put(payload).array()

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    fun ftyp(brand: String = "isom"): ByteArray = box("ftyp", concat(brand.toByteArray(), ByteArray(4), "isomiso2".toByteArray()))

    /** Matrix (a, b, c, d in 16.16) laid out as the nine stored ints. */
    fun matrix(a: Int, b: Int, c: Int, d: Int, tx: Int = 0, ty: Int = 0): IntArray = intArrayOf(a, b, 0, c, d, 0, tx, ty, 1 shl 30)

    val identity = matrix(ONE, 0, 0, ONE)
    val rotated90 = matrix(0, ONE, -ONE, 0)
    val rotated180 = matrix(-ONE, 0, 0, -ONE)
    val rotated270 = matrix(0, -ONE, ONE, 0)

    fun tkhd(matrix: IntArray, version: Int = 0): ByteArray {
        val matrixAt = if (version == 1) 52 else 40
        val payload = ByteBuffer.allocate(matrixAt + 36 + 8)
        payload.put(version.toByte())
        payload.position(matrixAt)
        matrix.forEach { payload.putInt(it) }
        return box("tkhd", payload.array())
    }

    fun hdlr(handler: String): ByteArray = box("hdlr", concat(ByteArray(8), handler.toByteArray(), ByteArray(12), "Handler\u0000".toByteArray()))

    fun trak(handler: String, matrix: IntArray, version: Int = 0): ByteArray =
        box("trak", concat(tkhd(matrix, version), box("mdia", concat(box("mdhd", ByteArray(24)), hdlr(handler), box("minf")))))

    fun moov(vararg tracks: ByteArray): ByteArray = box("moov", concat(box("mvhd", ByteArray(100)), *tracks))

    fun mdat(size: Int): ByteArray = box("mdat", ByteArray(size) { (it * 31).toByte() })

    /** Standard layout: ftyp, free, mdat, moov. */
    fun mp4(vararg tracks: ByteArray, brand: String = "isom"): ByteArray = concat(ftyp(brand), box("free", ByteArray(12)), mdat(500), moov(*tracks))

    fun matrixOf(file: ByteArray, tkhdIndex: Int = 0): IntArray {
        var from = 0
        var offset = -1
        repeat(tkhdIndex + 1) {
            offset = indexOf(file, "tkhd".toByteArray(), from)
            from = offset + 1
        }
        val version = file[offset + 4].toInt()
        val start = offset + 4 + if (version == 1) 52 else 40
        val buffer = ByteBuffer.wrap(file, start, 36)
        return IntArray(9) { buffer.getInt() }
    }

    private fun indexOf(data: ByteArray, needle: ByteArray, from: Int): Int {
        for (i in from..data.size - needle.size) {
            if (needle.indices.all { data[i + it] == needle[it] }) return i
        }
        return -1
    }
}
