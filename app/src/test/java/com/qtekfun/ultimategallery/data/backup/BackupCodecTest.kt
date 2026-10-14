package com.qtekfun.ultimategallery.data.backup

import android.net.Uri
import com.qtekfun.ultimategallery.data.prefs.AppSettings
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.ThemeMode
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCodecTest {
    private val settings = AppSettings(
        themeMode = ThemeMode.DARK,
        dynamicColor = false,
        hapticsEnabled = true,
        saveBehavior = SaveBehavior.COPY,
        folderGridColumns = 3,
        photoGridColumns = 5,
        autoHideApps = setOf("whatsapp", "telegram"),
        exportDefaults = ExportSettings(format = ExportFormat.WEBP, quality = 80, maxLongEdge = 2048, destination = "Pictures/Shop")
    )

    private fun backup(settingsJson: JSONObject? = null, profiles: List<JSONObject> = emptyList(), schema: Int = 1) = JSONObject().apply {
        put("format", BackupCodec.FORMAT)
        put("schema", schema)
        settingsJson?.let { put("settings", it) }
        put("profiles", JSONArray(profiles))
    }.toString()

    @Test
    fun settingsAndProfilesRoundTrip() {
        val profile = WatermarkProfile(
            name = "Shop",
            source = WatermarkSource.Tiled(WatermarkSource.Text("SOLD"), spacing = 1.2f),
            opacity = 0.4f,
            portrait = Placement(0.1f, 0.2f, 0.3f, 15f),
            landscape = Placement(0.7f, 0.8f, 0.2f, -5f)
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(settings, listOf(profile), "1.0.0"))
        assertNotNull(decoded)
        assertEquals(settings, decoded!!.settings)
        assertEquals(listOf(profile), decoded.profiles)
        assertFalse(decoded.newerSchema)
    }

    @Test
    fun internalStateIsNotExported() {
        val json = BackupCodec.encode(settings.copy(lastProfileId = 7, appFoldersOffered = true), emptyList(), "1.0.0")
        assertFalse(json.contains("lastProfile"))
        assertFalse(json.contains("appFoldersOffered"))
    }

    @Test
    fun otherFilesAreRejected() {
        assertNull(BackupCodec.decode("not json"))
        assertNull(BackupCodec.decode("{\"hello\": 1}"))
        assertNull(BackupCodec.decode("{\"format\": \"other\", \"schema\": 1}"))
        assertNull(BackupCodec.decode("{\"format\": \"${BackupCodec.FORMAT}\"}"))
    }

    @Test
    fun missingKeysAndUnknownEnumsFallBackToDefaults() {
        val decoded = BackupCodec.decode(backup(JSONObject().put("themeMode", "NEON").put("hapticsEnabled", true)))!!
        val d = AppSettings()
        assertEquals(d.themeMode, decoded.settings!!.themeMode)
        assertTrue(decoded.settings!!.hapticsEnabled)
        assertEquals(d.photoGridColumns, decoded.settings!!.photoGridColumns)
    }

    @Test
    fun valuesAreBroughtBackIntoRange() {
        val export = JSONObject().put("quality", 900).put("maxLongEdge", -5).put("pattern", "../{name}/x").put("destination", "/sdcard/../etc")
        val decoded = BackupCodec.decode(
            backup(JSONObject().put("folderGridColumns", 99).put("photoGridColumns", 0).put("exportDefaults", export))
        )!!.settings!!
        assertEquals(4, decoded.folderGridColumns)
        assertEquals(2, decoded.photoGridColumns)
        assertEquals(100, decoded.exportDefaults.quality)
        assertNull(decoded.exportDefaults.maxLongEdge)
        assertEquals("..{name}x", decoded.exportDefaults.fileNamePattern)
        assertEquals(ExportSettings().destination, decoded.exportDefaults.destination)
    }

    @Test
    fun placementsAndOpacityAreClamped() {
        val raw = JSONObject(com.qtekfun.ultimategallery.data.profile.ProfileCodec.toJson(WatermarkProfile(name = "  Odd   name ")))
        raw.put("opacity", 7.0)
        raw.put("portrait", JSONObject().put("cx", -3.0).put("cy", 9.0).put("size", 0.0).put("rot", 5000.0))
        val profile = BackupCodec.decode(backup(profiles = listOf(raw)))!!.profiles.single()
        assertEquals(1f, profile.opacity, 0f)
        assertEquals(Placement(0f, 1f, 0.01f, 360f), profile.portrait)
        assertEquals("Odd name", profile.name)
        assertEquals(0L, profile.id)
    }

    @Test
    fun imageWatermarksAreReplacedByText() {
        val withImage = WatermarkProfile(name = "Logo", source = WatermarkSource.Image(Uri.parse("file:///data/user/0/app/logo.png")))
        val tiled = WatermarkProfile(name = "Tiles", source = WatermarkSource.Tiled(WatermarkSource.Image(Uri.parse("content://x/1"))))
        val decoded = BackupCodec.decode(BackupCodec.encode(settings, listOf(withImage, tiled), "1.0.0"))!!
        assertTrue(decoded.profiles.none { BackupCodec.hasImage(it.source) })
        assertEquals(listOf("Logo", "Tiles"), decoded.profiles.map { it.name })
    }

    @Test
    fun newerSchemaIsFlagged() {
        assertTrue(BackupCodec.decode(backup(schema = BackupCodec.SCHEMA + 1))!!.newerSchema)
    }
}
