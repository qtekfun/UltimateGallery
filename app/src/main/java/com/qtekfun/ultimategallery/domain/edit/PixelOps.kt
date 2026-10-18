package com.qtekfun.ultimategallery.domain.edit

/** Pixel operations that have no framework equivalent, on ARGB rows. */
object PixelOps {
    private const val MAX_AMOUNT = 1.5f
    private const val BYTE_MASK = 0xFF

    /**
     * Unsharp mask with a 3×3 box: each channel moves away from its local average by [amount] (0..1, up to 1.5 times the
     * difference). [rows] holds [rowCount] rows of [width] pixels; only rows [from] until [to] are written to the result,
     * which has `to - from` rows. Rows outside the band are only read, so a photo can be processed in bands with one row
     * of overlap. Alpha is kept.
     */
    fun sharpen(rows: IntArray, width: Int, rowCount: Int, from: Int, to: Int, amount: Float): IntArray {
        val k = amount.coerceIn(0f, 1f) * MAX_AMOUNT
        val out = IntArray((to - from) * width)
        for (y in from until to) {
            for (x in 0 until width) {
                val center = rows[y * width + x]
                var r = 0
                var g = 0
                var b = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, rowCount - 1)
                    for (dx in -1..1) {
                        val p = rows[yy * width + (x + dx).coerceIn(0, width - 1)]
                        r += (p shr 16) and BYTE_MASK
                        g += (p shr 8) and BYTE_MASK
                        b += p and BYTE_MASK
                    }
                }
                fun channel(value: Int, sum: Int): Int = (value + k * (value - sum / 9f)).toInt().coerceIn(0, BYTE_MASK)
                val cr = channel((center shr 16) and BYTE_MASK, r)
                val cg = channel((center shr 8) and BYTE_MASK, g)
                val cb = channel(center and BYTE_MASK, b)
                out[(y - from) * width + x] = (center and 0xFF000000.toInt()) or (cr shl 16) or (cg shl 8) or cb
            }
        }
        return out
    }
}
