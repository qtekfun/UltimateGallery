package com.qtekfun.ultimategallery.data.profile

import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileCodecTest {
    private fun roundTrip(profile: WatermarkProfile) = ProfileCodec.fromJson(ProfileCodec.toJson(profile))

    private val style = TextStyleSpec(
        fontId = "imported:My Font.ttf",
        weight = 800,
        italic = true,
        colorArgb = 0xFF112233.toInt(),
        outlineEnabled = true,
        outlineColorArgb = 0xFF445566.toInt(),
        outlineWidth = 0.15f,
        shadowEnabled = false,
        shadowColorArgb = 0x80778899.toInt(),
        shadowRadius = 0.2f,
        backgroundEnabled = true,
        backgroundColorArgb = 0x66AABBCC,
        backgroundPadding = 0.45f
    )

    private val settings = ExportSettings(ExportFormat.WEBP, 71, 2048, "{n}-{name}", "DCIM/Shop", ExifMode.KEEP)

    private fun profile(source: WatermarkSource) = WatermarkProfile(
        id = 42,
        name = "Shop \"quoted\" ñ",
        source = source,
        opacity = 0.4f,
        portrait = Placement(0.1f, 0.2f, 0.3f, -15f),
        landscape = Placement(0.9f, 0.8f, 0.15f, 33.5f),
        export = settings,
        margin = 0.07f
    )

    @Test
    fun textRoundTrips() {
        val p = profile(WatermarkSource.Text("@shop\nline", style))
        assertEquals(p, roundTrip(p))
    }

    private fun textWith(styleJson: String) = ProfileCodec.fromJson(
        """{"name":"X","source":{"type":"text","text":"hi","style":$styleJson}}"""
    ).source.let { (it as WatermarkSource.Text).style }

    @Test
    fun legacyFontNamesMigrateToFontIds() {
        val expected = mapOf(
            "SANS" to FontIds.DEFAULT,
            "SERIF" to "bundled:PlayfairDisplay",
            "MONOSPACE" to "bundled:RobotoMono",
            "CURSIVE" to "bundled:DancingScript",
            "CONDENSED" to "bundled:Oswald",
            "WHATEVER" to FontIds.DEFAULT
        )
        expected.forEach { (legacy, id) -> assertEquals(legacy, id, textWith("""{"font":"$legacy","weight":300}""").fontId) }
        assertEquals(300, textWith("""{"font":"SERIF","weight":300}""").weight)
    }

    @Test
    fun fontIdWinsOverLegacyFontAndUnknownIdsLoad() {
        assertEquals("imported:x.ttf", textWith("""{"font":"SERIF","fontId":"imported:x.ttf"}""").fontId)
        assertEquals("future:thing", textWith("""{"fontId":"future:thing"}""").fontId)
        assertEquals(FontIds.DEFAULT, textWith("{}").fontId)
        assertEquals(FontIds.DEFAULT, TextStyleSpec().fontId)
        assertTrue(ProfileCodec.toJson(profile(WatermarkSource.Text("a", style))).contains("\"fontId\""))
    }

    @Test
    fun imageRoundTrips() {
        val p = profile(WatermarkSource.Image(Uri.parse("file:///data/logo%20x.png")))
        assertEquals(p, roundTrip(p))
    }

    @Test
    fun tiledWithNestedBaseRoundTrips() {
        val p = profile(WatermarkSource.Tiled(WatermarkSource.Text("tile", style), 0.9f, 0.25f, 0.5f))
        assertEquals(p, roundTrip(p))
        val q = profile(WatermarkSource.Tiled(WatermarkSource.Image(Uri.parse("file:///l.png"))))
        assertEquals(q, roundTrip(q))
    }

    @Test
    fun nullMaxLongEdgeStaysNull() {
        val p = profile(WatermarkSource.Text("a")).copy(export = ExportSettings(maxLongEdge = null))
        assertEquals(null, roundTrip(p).export.maxLongEdge)
    }

    @Test
    fun defaultsRoundTrip() {
        assertEquals(WatermarkProfile(), roundTrip(WatermarkProfile()))
    }

    @Test
    fun missingKeysUseDefaults() {
        assertEquals(WatermarkProfile(), ProfileCodec.fromJson("{}"))
        val partial = ProfileCodec.fromJson("""{"name":"X","landscape":{"cx":0.5},"export":{"format":"PNG"},"source":{"type":"text","text":"hi"}}""")
        assertEquals("X", partial.name)
        assertEquals(0.5f, partial.landscape.centerX, 0f)
        assertEquals(Placement.DefaultLandscape.centerY, partial.landscape.centerY, 0f)
        assertEquals(ExportFormat.PNG, partial.export.format)
        assertEquals(ExportSettings().quality, partial.export.quality)
        assertEquals(WatermarkSource.Text("hi"), partial.source)
    }

    @Test
    fun unknownEnumValuesFallBackToDefaults() {
        val p = ProfileCodec.fromJson("""{"export":{"format":"BMP","exif":"???"}}""")
        assertEquals(ExportSettings().format, p.export.format)
        assertEquals(ExportSettings().exif, p.export.exif)
    }
}
