package com.qtekfun.ultimategallery.feature.watermark

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import kotlin.math.roundToInt

internal fun WatermarkSource.leaf(): WatermarkSource = if (this is WatermarkSource.Tiled) base.leaf() else this

private fun WatermarkSource.type(): MarkType = when {
    this is WatermarkSource.Tiled -> MarkType.TILED
    this is WatermarkSource.Image -> MarkType.IMAGE
    else -> MarkType.TEXT
}

/** The "Mark" tab: what the watermark is. */
@Composable
fun MarkPanel(profile: WatermarkProfile, actions: EditorActions, modifier: Modifier = Modifier) {
    val source = profile.source
    val leaf = source.leaf()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(actions.onImportLogo)
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val types = listOf(
            MarkType.TEXT to R.string.type_text,
            MarkType.IMAGE to R.string.type_image,
            MarkType.TILED to R.string.type_tiled
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { i, (type, label) ->
                SegmentedButton(
                    selected = source.type() == type,
                    onClick = {
                        if (type == MarkType.IMAGE && leaf !is WatermarkSource.Image) {
                            actions.onSetType(type)
                            if (!actions.hasLogo()) picker.launch(arrayOf("image/*"))
                        } else {
                            actions.onSetType(type)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, types.size)
                ) { Text(stringResource(label)) }
            }
        }
        if (source is WatermarkSource.Tiled) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val bases = listOf(true to R.string.type_text, false to R.string.type_image)
                bases.forEachIndexed { i, (isText, label) ->
                    SegmentedButton(
                        selected = (leaf is WatermarkSource.Text) == isText,
                        onClick = {
                            if (isText) {
                                actions.onSetBase(actions.lastText())
                            } else if (actions.hasLogo()) {
                                actions.onSetBase(actions.lastImage()!!)
                            } else {
                                picker.launch(arrayOf("image/*"))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, bases.size)
                    ) { Text(stringResource(label)) }
                }
            }
        }
        when (leaf) {
            is WatermarkSource.Text -> OutlinedTextField(
                value = leaf.text,
                onValueChange = actions.onText,
                label = { Text(stringResource(R.string.text_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            is WatermarkSource.Image -> {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Text(stringResource(R.string.change_logo), Modifier.padding(start = 8.dp))
                }
                Text(
                    stringResource(R.string.logo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is WatermarkSource.Tiled -> Unit
        }
        if (source is WatermarkSource.Tiled) {
            LabeledSlider(stringResource(R.string.tiled_spacing), source.spacing, 0f..3f, actions.onSpacing)
            LabeledSlider(stringResource(R.string.tiled_stagger), source.staggerX, 0f..1f, actions.onStagger)
        }
    }
}

/** The "Placement" tab: margins, snapping and per-orientation placement. */
@Composable
fun PlacementPanel(profile: WatermarkProfile, orientation: Orientation, snapEnabled: Boolean, actions: EditorActions, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(
                if (orientation ==
                    Orientation.PORTRAIT
                ) {
                    R.string.placement_for_portrait
                } else {
                    R.string.placement_for_landscape
                }
            ),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            stringResource(R.string.placement_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ToggleRow(stringResource(R.string.snapping), snapEnabled, actions.onSnap)
        LabeledSlider(
            stringResource(R.string.margin),
            profile.margin,
            0f..0.15f,
            actions.onMargin,
            valueText = "${(profile.margin * 100).roundToInt()}%"
        )
        Button(onClick = actions.onResetPlacement) { Text(stringResource(R.string.reset_position)) }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    steps: Int = 0
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (valueText !=
                null
            ) {
                Text(
                    valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Callbacks of the editor, grouped so the panels do not take a dozen parameters each. */
class EditorActions(
    val onSetType: (MarkType) -> Unit,
    val onSetBase: (WatermarkSource) -> Unit,
    val onText: (String) -> Unit,
    val onStyle: (TextStyleSpec) -> Unit,
    val onSpacing: (Float) -> Unit,
    val onStagger: (Float) -> Unit,
    val onImportLogo: (Uri) -> Unit,
    val hasLogo: () -> Boolean,
    val lastText: () -> WatermarkSource.Text,
    val lastImage: () -> WatermarkSource.Image?,
    val onOpacity: (Float) -> Unit,
    val onMargin: (Float) -> Unit,
    val onSnap: (Boolean) -> Unit,
    val onResetPlacement: () -> Unit
)
