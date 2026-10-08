package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.export.ExportPaths
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import kotlin.math.roundToInt

private val SizeChoices = listOf(null, 4096, 3000, 2048, 1600, 1080)
private val DestinationPresets = listOf("Pictures/UltimateGallery", "Pictures/Watermarked", "DCIM/UltimateGallery")

/** The "Export" tab: where and how the watermarked copies are written. */
@Composable
fun ExportPanel(settings: ExportSettings, onChange: (ExportSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.export_originals_untouched),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val destinationValid = ExportPaths.isValidDestination(settings.destination)
        OutlinedTextField(
            value = settings.destination,
            onValueChange = { onChange(settings.copy(destination = it)) },
            label = { Text(stringResource(R.string.export_destination)) },
            isError = !destinationValid,
            supportingText = if (destinationValid) null else ({ Text(stringResource(R.string.export_destination_error)) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DestinationPresets.forEach { preset ->
                FilterChip(
                    selected = ExportPaths.normalize(settings.destination) == ExportPaths.normalize(preset),
                    onClick = { onChange(settings.copy(destination = preset)) },
                    label = { Text(preset) }
                )
            }
        }

        Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelLarge)
        val formats = ExportFormat.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            formats.forEachIndexed { i, format ->
                SegmentedButton(
                    selected = settings.format == format,
                    onClick = { onChange(settings.copy(format = format)) },
                    shape = SegmentedButtonDefaults.itemShape(i, formats.size)
                ) { Text(if (format == ExportFormat.WEBP) "WebP" else format.name) }
            }
        }
        if (settings.format != ExportFormat.PNG) {
            LabeledSlider(
                stringResource(R.string.export_quality),
                settings.quality.toFloat(),
                40f..100f,
                { onChange(settings.copy(quality = it.roundToInt())) },
                valueText = "${settings.quality}%"
            )
        }

        Text(stringResource(R.string.export_resize), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SizeChoices.forEach { size ->
                FilterChip(
                    selected = settings.maxLongEdge == size,
                    onClick = { onChange(settings.copy(maxLongEdge = size)) },
                    label = { Text(size?.let { "$it px" } ?: stringResource(R.string.export_original_size)) }
                )
            }
        }

        OutlinedTextField(
            value = settings.fileNamePattern,
            onValueChange = { onChange(settings.copy(fileNamePattern = it)) },
            label = { Text(stringResource(R.string.export_pattern)) },
            supportingText = {
                Text(
                    stringResource(R.string.export_pattern_hint) + " · " +
                        stringResource(R.string.export_pattern_example, ExportPaths.exampleName(settings))
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(stringResource(R.string.export_exif), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.selectableGroup()) {
            listOf(
                ExifMode.STRIP_LOCATION to R.string.exif_strip_location,
                ExifMode.KEEP to R.string.exif_keep,
                ExifMode.STRIP_ALL to R.string.exif_strip_all
            ).forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = settings.exif == mode,
                        onClick = { onChange(settings.copy(exif = mode)) },
                        role = Role.RadioButton
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.exif == mode, onClick = null)
                    Text(stringResource(label), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                }
            }
        }
    }
}
