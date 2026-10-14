package com.qtekfun.ultimategallery.data.backup

import com.qtekfun.ultimategallery.data.prefs.AppSettings
import com.qtekfun.ultimategallery.data.profile.ProfileCodec
import com.qtekfun.ultimategallery.data.profile.ProfileNaming
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.feature.gallery.MAX_FOLDER_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MAX_PHOTO_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MIN_FOLDER_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MIN_PHOTO_COLUMNS
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The file format of a settings backup: one small JSON document with the user's preferences and
 * watermark profiles. Photos are never part of it.
 *
 * Reading is tolerant (missing keys use defaults, unknown enum names are ignored) and defensive: a
 * backup is a foreign file, so every value is brought back into the range the app itself allows.
 */
@Suppress("TooManyFunctions")
object BackupCodec {
    const val FORMAT = "ultimategallery-settings"
    const val SCHEMA = 1

    /** Larger files are not backups; refusing them keeps a wrong pick from being loaded into memory. */
    const val MAX_BYTES = 1_000_000
    const val MAX_PROFILES = 100
    private const val MAX_APP_IDS = 100
    private const val MAX_APP_ID_LENGTH = 64
    private const val MAX_LONG_EDGE = 20_000
    private const val MAX_FILE_PATTERN = 100
    private const val MAX_DESTINATION = 200
    private const val MAX_ROTATION = 360f
    private const val MIN_SIZE_FRACTION = 0.01f
    private const val MAX_MARGIN = 0.5f

    /**
     * What a backup holds. [settings] is null when the file has none; [newerSchema] tells that it was
     * written by a newer version of the app, so some of its content may have been ignored.
     */
    data class Contents(val settings: AppSettings?, val profiles: List<WatermarkProfile>, val newerSchema: Boolean)

    fun encode(settings: AppSettings, profiles: List<WatermarkProfile>, appVersion: String): String = JSONObject().apply {
        put("format", FORMAT)
        put("schema", SCHEMA)
        put("appVersion", appVersion)
        put("settings", settingsToJson(settings))
        put("profiles", JSONArray().apply { profiles.forEach { put(JSONObject(ProfileCodec.toJson(portable(it)))) } })
    }.toString(2)

    /** Parses [json]; null when it is not a settings backup of this app. */
    fun decode(json: String): Contents? {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        if (root.optString("format") != FORMAT) return null
        val schema = root.optInt("schema", 0)
        if (schema < 1) return null
        val profiles = root.optJSONArray("profiles")?.let { array ->
            (0 until minOf(array.length(), MAX_PROFILES)).mapNotNull { array.optJSONObject(it) }
                .map { portable(ProfileCodec.fromJson(it.toString())) }
        }.orEmpty()
        return Contents(
            settings = root.optJSONObject("settings")?.let(::settingsFromJson),
            profiles = profiles,
            newerSchema = schema > SCHEMA
        )
    }

    /**
     * The profile as it travels between installs: no id, a clean name, values in range and no image
     * watermark (an image only exists in this install's private storage, so it is replaced by the
     * default text mark).
     */
    fun portable(p: WatermarkProfile): WatermarkProfile {
        val d = WatermarkProfile()
        return p.copy(
            id = 0,
            name = ProfileNaming.clean(p.name).ifEmpty { d.name },
            source = if (hasImage(p.source)) d.source else p.source,
            opacity = p.opacity.finiteOr(d.opacity).coerceIn(0f, 1f),
            portrait = sanitize(p.portrait, d.portrait),
            landscape = sanitize(p.landscape, d.landscape),
            export = sanitize(p.export),
            margin = p.margin.finiteOr(d.margin).coerceIn(0f, MAX_MARGIN)
        )
    }

    fun hasImage(source: WatermarkSource): Boolean = when (source) {
        is WatermarkSource.Image -> true
        is WatermarkSource.Tiled -> hasImage(source.base)
        is WatermarkSource.Text -> false
    }

    private fun settingsToJson(s: AppSettings) = JSONObject().apply {
        put("homeViewMode", s.homeViewMode.name)
        put("folderGridColumns", s.folderGridColumns)
        put("photoGridColumns", s.photoGridColumns)
        put("themeMode", s.themeMode.name)
        put("dynamicColor", s.dynamicColor)
        put("hapticsEnabled", s.hapticsEnabled)
        put("saveBehavior", s.saveBehavior.name)
        put("autoHideApps", JSONArray(s.autoHideApps.sorted()))
        put("exportDefaults", ProfileCodec.exportToJson(s.exportDefaults))
    }

    private fun settingsFromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        val apps = o.optJSONArray("autoHideApps")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { id -> id.isNotBlank() && id.length <= MAX_APP_ID_LENGTH } }
        }.orEmpty()
        return AppSettings(
            homeViewMode = enumOrDefault(o.optString("homeViewMode"), d.homeViewMode),
            folderGridColumns = o.optInt("folderGridColumns", d.folderGridColumns).coerceIn(MIN_FOLDER_COLUMNS, MAX_FOLDER_COLUMNS),
            photoGridColumns = o.optInt("photoGridColumns", d.photoGridColumns).coerceIn(MIN_PHOTO_COLUMNS, MAX_PHOTO_COLUMNS),
            themeMode = enumOrDefault(o.optString("themeMode"), d.themeMode),
            dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
            hapticsEnabled = o.optBoolean("hapticsEnabled", d.hapticsEnabled),
            saveBehavior = enumOrDefault(o.optString("saveBehavior"), d.saveBehavior),
            autoHideApps = apps.take(MAX_APP_IDS).toSet(),
            exportDefaults = sanitize(o.optJSONObject("exportDefaults")?.let(ProfileCodec::exportFromJson) ?: d.exportDefaults)
        )
    }

    private fun sanitize(p: Placement, default: Placement) = Placement(
        centerX = p.centerX.finiteOr(default.centerX).coerceIn(0f, 1f),
        centerY = p.centerY.finiteOr(default.centerY).coerceIn(0f, 1f),
        sizeFraction = p.sizeFraction.finiteOr(default.sizeFraction).coerceIn(MIN_SIZE_FRACTION, 1f),
        rotationDeg = p.rotationDeg.finiteOr(default.rotationDeg).coerceIn(-MAX_ROTATION, MAX_ROTATION)
    )

    private fun sanitize(e: ExportSettings): ExportSettings {
        val d = ExportSettings()
        val pattern = e.fileNamePattern.filterNot { it == '/' || it == '\\' }.trim().take(MAX_FILE_PATTERN)
        return e.copy(
            quality = e.quality.coerceIn(1, 100),
            maxLongEdge = e.maxLongEdge?.takeIf { it > 0 }?.coerceAtMost(MAX_LONG_EDGE),
            fileNamePattern = pattern.ifEmpty { d.fileNamePattern },
            destination = safeDestination(e.destination) ?: d.destination
        )
    }

    /** A relative folder under the volume root, without `..` or empty parts; null when nothing usable is left. */
    private fun safeDestination(path: String): String? {
        val parts = path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/").take(MAX_DESTINATION)
    }

    private fun Float.finiteOr(default: Float) = if (isFinite()) this else default

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
}
