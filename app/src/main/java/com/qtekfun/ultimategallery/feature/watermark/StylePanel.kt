package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.FontInfo
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import kotlin.math.roundToInt

private const val WEIGHT_STEPS = 7
private const val SAMPLE_HEIGHT_DP = 64
private const val SAMPLE_BACKDROP = 0xFF808080
private const val SAMPLE_FILL = 0.8f

/** The "Style" tab: how a text mark looks. A live sample stays pinned above the scrolling controls. */
@Composable
fun StylePanel(profile: WatermarkProfile, actions: EditorActions, modifier: Modifier = Modifier, fonts: List<FontInfo> = emptyList()) {
    val leaf = profile.source.leaf()
    if (leaf !is WatermarkSource.Text) {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.logo_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OpacitySlider(profile, actions)
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StyleSample(leaf, profile.opacity)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextStyleControls(leaf, fonts, actions)
            OpacitySlider(profile, actions)
        }
    }
}

@Composable
private fun OpacitySlider(profile: WatermarkProfile, actions: EditorActions) {
    LabeledSlider(
        stringResource(R.string.opacity),
        profile.opacity,
        0f..1f,
        actions.onOpacity,
        valueText = "${(profile.opacity * 100).roundToInt()}%"
    )
}

/** The mark drawn by the shared renderer on a neutral backdrop, so every change is visible at once. */
@Composable
private fun StyleSample(text: WatermarkSource.Text, opacity: Float) {
    val renderer = remember { WatermarkRenderer { null } }
    val description = stringResource(R.string.style_preview)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(SAMPLE_HEIGHT_DP.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(SAMPLE_BACKDROP))
            .semantics { contentDescription = description }
    ) {
        // Fit the mark inside the box, whatever its width-to-height ratio.
        val fitHeight = SAMPLE_FILL * size.height * renderer.markAspect(text) / size.width
        renderer.draw(
            drawContext.canvas.nativeCanvas,
            size.width.roundToInt(),
            size.height.roundToInt(),
            text,
            opacity,
            Placement(0.5f, 0.5f, minOf(SAMPLE_FILL, fitHeight), 0f)
        )
    }
}

@Composable
private fun TextStyleControls(text: WatermarkSource.Text, fonts: List<FontInfo>, actions: EditorActions) {
    val style = text.style
    val set = actions.onStyle
    var pickerOpen by remember { mutableStateOf(false) }
    FontButton(style.fontId, fonts, actions) { pickerOpen = true }
    if (pickerOpen) {
        FontPickerSheet(
            fonts = fonts,
            selectedId = style.fontId,
            sampleText = text.text,
            actions = actions,
            onSelect = { set(style.copy(fontId = it)) },
            onDismiss = { pickerOpen = false }
        )
    }
    FilterChip(
        selected = style.italic,
        onClick = { set(style.copy(italic = !style.italic)) },
        label = { Text(stringResource(R.string.italic), fontStyle = FontStyle.Italic) }
    )
    LabeledSlider(
        stringResource(R.string.weight),
        style.weight.toFloat(),
        MIN_WEIGHT..MAX_WEIGHT,
        { set(style.copy(weight = (it / WEIGHT_STEP).roundToInt() * WEIGHT_STEP.toInt())) },
        valueText = style.weight.toString(),
        steps = WEIGHT_STEPS
    )
    Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
    ColorRow(style.colorArgb, { set(style.copy(colorArgb = it)) })

    ToggleRow(stringResource(R.string.outline), style.outlineEnabled) { set(style.copy(outlineEnabled = it)) }
    if (style.outlineEnabled) {
        Text(stringResource(R.string.outline_color), style = MaterialTheme.typography.labelLarge)
        ColorRow(style.outlineColorArgb, { set(style.copy(outlineColorArgb = it)) })
        LabeledSlider(
            stringResource(R.string.outline_width),
            style.outlineWidth,
            0.02f..0.3f,
            { set(style.copy(outlineWidth = it)) },
            valueText = "${(style.outlineWidth * PERCENT).roundToInt()}%"
        )
    }

    ToggleRow(stringResource(R.string.shadow), style.shadowEnabled) { set(style.copy(shadowEnabled = it)) }
    if (style.shadowEnabled) {
        Text(stringResource(R.string.shadow_color), style = MaterialTheme.typography.labelLarge)
        ColorRow(style.shadowColorArgb, { set(style.copy(shadowColorArgb = it)) })
        LabeledSlider(
            stringResource(R.string.shadow_softness),
            style.shadowRadius,
            0.01f..0.4f,
            { set(style.copy(shadowRadius = it)) },
            valueText = "${(style.shadowRadius * PERCENT).roundToInt()}%"
        )
    }

    ToggleRow(stringResource(R.string.background_pill), style.backgroundEnabled) { set(style.copy(backgroundEnabled = it)) }
    if (style.backgroundEnabled) {
        Text(stringResource(R.string.background_color), style = MaterialTheme.typography.labelLarge)
        ColorRow(style.backgroundColorArgb, { set(style.copy(backgroundColorArgb = it)) })
        LabeledSlider(
            stringResource(R.string.background_padding),
            style.backgroundPadding,
            0f..1f,
            { set(style.copy(backgroundPadding = it)) },
            valueText = "${(style.backgroundPadding * PERCENT).roundToInt()}%"
        )
    }
}

/** One button naming the current font in its own typeface; it opens the font picker. */
@Composable
private fun FontButton(fontId: String, fonts: List<FontInfo>, actions: EditorActions, onClick: () -> Unit) {
    val family = rememberFontFamily(fontId, actions.typefaceOf)
    val name = fonts.firstOrNull { it.id == fontId }?.label
        ?: stringResource(R.string.font_default_name).takeIf { fontId == FontIds.DEFAULT }
        ?: fontId.substringAfter(':')
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("font_button")) {
        Icon(Icons.Outlined.TextFields, contentDescription = null)
        Text(stringResource(R.string.font_label), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        Text(name, Modifier.weight(1f).padding(start = 12.dp), fontFamily = family, maxLines = 1, textAlign = TextAlign.End)
    }
}

private const val MIN_WEIGHT = 100f
private const val MAX_WEIGHT = 900f
private const val WEIGHT_STEP = 100f
private const val PERCENT = 100
