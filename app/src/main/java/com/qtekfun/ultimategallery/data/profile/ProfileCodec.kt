package com.qtekfun.ultimategallery.data.profile

import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.json.JSONObject

/**
 * JSON form of a [WatermarkProfile], used to hand a profile to a background job. Missing keys fall
 * back to the defaults of the model, so older or partial documents still load.
 */
@Suppress("TooManyFunctions")
object ProfileCodec {
    fun toJson(profile: WatermarkProfile): String = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("source", sourceToJson(profile.source))
        put("opacity", profile.opacity.toDouble())
        put("portrait", placementToJson(profile.portrait))
        put("landscape", placementToJson(profile.landscape))
        put("export", exportToJson(profile.export))
        put("margin", profile.margin.toDouble())
    }.toString()

    fun fromJson(json: String): WatermarkProfile {
        val o = JSONObject(json)
        val d = WatermarkProfile()
        return WatermarkProfile(
            id = o.optLong("id", d.id),
            name = o.optString("name", d.name),
            source = o.optJSONObject("source")?.let(::sourceFromJson) ?: d.source,
            opacity = o.optFloat("opacity", d.opacity),
            portrait = o.optJSONObject("portrait")?.let { placementFromJson(it, d.portrait) } ?: d.portrait,
            landscape = o.optJSONObject("landscape")?.let { placementFromJson(it, d.landscape) } ?: d.landscape,
            export = o.optJSONObject("export")?.let(::exportFromJson) ?: d.export,
            margin = o.optFloat("margin", d.margin)
        )
    }

    private fun JSONObject.optFloat(key: String, default: Float): Float = if (has(key) &&
        !isNull(key)
    ) {
        optDouble(key, default.toDouble()).toFloat()
    } else {
        default
    }

    private fun placementToJson(p: Placement) = JSONObject().apply {
        put("cx", p.centerX.toDouble())
        put("cy", p.centerY.toDouble())
        put("size", p.sizeFraction.toDouble())
        put("rot", p.rotationDeg.toDouble())
    }

    private fun placementFromJson(o: JSONObject, d: Placement) = Placement(
        centerX = o.optFloat("cx", d.centerX),
        centerY = o.optFloat("cy", d.centerY),
        sizeFraction = o.optFloat("size", d.sizeFraction),
        rotationDeg = o.optFloat("rot", d.rotationDeg)
    )

    private fun exportToJson(e: ExportSettings) = JSONObject().apply {
        put("format", e.format.name)
        put("quality", e.quality)
        if (e.maxLongEdge != null) put("maxLongEdge", e.maxLongEdge)
        put("pattern", e.fileNamePattern)
        put("destination", e.destination)
        put("exif", e.exif.name)
    }

    private fun exportFromJson(o: JSONObject): ExportSettings {
        val d = ExportSettings()
        return ExportSettings(
            format = enumOrDefault(o.optString("format"), d.format),
            quality = o.optInt("quality", d.quality),
            maxLongEdge = if (o.has("maxLongEdge") && !o.isNull("maxLongEdge")) o.optInt("maxLongEdge") else null,
            fileNamePattern = o.optString("pattern", d.fileNamePattern),
            destination = o.optString("destination", d.destination),
            exif = enumOrDefault(o.optString("exif"), d.exif)
        )
    }

    private fun sourceToJson(s: WatermarkSource): JSONObject = JSONObject().apply {
        when (s) {
            is WatermarkSource.Image -> {
                put("type", "image")
                put("uri", s.uri.toString())
            }
            is WatermarkSource.Text -> {
                put("type", "text")
                put("text", s.text)
                put("style", styleToJson(s.style))
            }
            is WatermarkSource.Tiled -> {
                put("type", "tiled")
                put("base", sourceToJson(s.base))
                put("spacing", s.spacing.toDouble())
                put("staggerX", s.staggerX.toDouble())
                put("staggerY", s.staggerY.toDouble())
            }
        }
    }

    private fun sourceFromJson(o: JSONObject): WatermarkSource? = when (o.optString("type")) {
        "image" -> o.optString("uri").takeIf { it.isNotEmpty() }?.let { WatermarkSource.Image(Uri.parse(it)) }
        "text" -> WatermarkSource.Text(o.optString("text"), o.optJSONObject("style")?.let(::styleFromJson) ?: TextStyleSpec())
        "tiled" -> {
            val d = WatermarkSource.Tiled(WatermarkSource.Text(""))
            o.optJSONObject("base")?.let(::sourceFromJson)?.let { base ->
                WatermarkSource.Tiled(
                    base = base,
                    spacing = o.optFloat("spacing", d.spacing),
                    staggerX = o.optFloat("staggerX", d.staggerX),
                    staggerY = o.optFloat("staggerY", d.staggerY)
                )
            }
        }
        else -> null
    }

    private fun styleToJson(s: TextStyleSpec) = JSONObject().apply {
        put("fontId", s.fontId)
        put("weight", s.weight)
        put("italic", s.italic)
        put("color", s.colorArgb)
        put("outline", s.outlineEnabled)
        put("outlineColor", s.outlineColorArgb)
        put("outlineWidth", s.outlineWidth.toDouble())
        put("shadow", s.shadowEnabled)
        put("shadowColor", s.shadowColorArgb)
        put("shadowRadius", s.shadowRadius.toDouble())
        put("background", s.backgroundEnabled)
        put("backgroundColor", s.backgroundColorArgb)
        put("backgroundPadding", s.backgroundPadding.toDouble())
    }

    private fun styleFromJson(o: JSONObject): TextStyleSpec {
        val d = TextStyleSpec()
        return TextStyleSpec(
            fontId = fontIdFrom(o, d.fontId),
            weight = o.optInt("weight", d.weight),
            italic = o.optBoolean("italic", d.italic),
            colorArgb = o.optInt("color", d.colorArgb),
            outlineEnabled = o.optBoolean("outline", d.outlineEnabled),
            outlineColorArgb = o.optInt("outlineColor", d.outlineColorArgb),
            outlineWidth = o.optFloat("outlineWidth", d.outlineWidth),
            shadowEnabled = o.optBoolean("shadow", d.shadowEnabled),
            shadowColorArgb = o.optInt("shadowColor", d.shadowColorArgb),
            shadowRadius = o.optFloat("shadowRadius", d.shadowRadius),
            backgroundEnabled = o.optBoolean("background", d.backgroundEnabled),
            backgroundColorArgb = o.optInt("backgroundColor", d.backgroundColorArgb),
            backgroundPadding = o.optFloat("backgroundPadding", d.backgroundPadding)
        )
    }

    /** Reads `fontId`; profiles saved before font ids only have the legacy `font` enum name. */
    private fun fontIdFrom(o: JSONObject, default: String): String {
        val id = o.optString("fontId")
        if (id.isNotEmpty()) return id
        return when (o.optString("font")) {
            "SERIF" -> FontIds.bundled("PlayfairDisplay")
            "MONOSPACE" -> FontIds.bundled("RobotoMono")
            "CURSIVE" -> FontIds.bundled("DancingScript")
            "CONDENSED" -> FontIds.bundled("Oswald")
            else -> default
        }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
}
