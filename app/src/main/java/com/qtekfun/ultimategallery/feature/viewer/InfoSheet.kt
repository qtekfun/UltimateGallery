package com.qtekfun.ultimategallery.feature.viewer

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import java.util.Date
import java.util.Locale

/** The info panel: file facts, camera data and, when present, where the photo was taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(details: MediaDetails?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            if (details != null) DetailRows(details)
        }
    }
}

@Composable
private fun DetailRows(d: MediaDetails) {
    val context = LocalContext.current
    DetailRow(R.string.info_name, d.name)
    DetailRow(
        R.string.info_date,
        DateFormat.getMediumDateFormat(context).format(Date(d.dateMs)) + "  " + DateFormat.getTimeFormat(context).format(Date(d.dateMs))
    )
    DetailRow(R.string.info_size, DetailsFormat.size(d.sizeBytes))
    if (d.width > 0 && d.height > 0) {
        DetailRow(R.string.info_resolution, stringResource(R.string.info_megapixels, d.width, d.height, String.format(Locale.US, "%.1f", d.megapixels)))
    }
    d.durationMs?.let { DetailRow(R.string.info_duration, DetailsFormat.duration(it)) }
    DetailRow(R.string.info_type, d.mimeType)
    d.path?.let { DetailRow(R.string.info_path, it) }
    d.camera?.let { DetailRow(R.string.info_camera, it) }
    d.lens?.let { DetailRow(R.string.info_lens, it) }
    val shot = listOfNotNull(d.aperture, d.exposure, d.iso, d.focalLength).joinToString("  ·  ")
    if (shot.isNotEmpty()) DetailRow(R.string.info_exposure, shot)
    if (d.latitude != null && d.longitude != null) {
        DetailRow(R.string.info_location, DetailsFormat.coordinates(d.latitude, d.longitude))
        OutlinedButton(
            onClick = {
                val geo = Uri.parse("geo:${d.latitude},${d.longitude}?q=${d.latitude},${d.longitude}")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
            },
            modifier = Modifier.padding(top = 4.dp)
        ) { Text(stringResource(R.string.info_open_map)) }
    }
}

@Composable
private fun DetailRow(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
