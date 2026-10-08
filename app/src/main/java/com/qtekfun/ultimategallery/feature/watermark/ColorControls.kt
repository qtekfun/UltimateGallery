package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R

private val Swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A, 0xFF9E9E9E
).map { it.toInt() }

/** A row of color swatches plus a custom hex entry. The alpha of [selected] is preserved. */
@Composable
fun ColorRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val alpha = selected ushr 24
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatches.forEach { argb ->
            val isSelected = (selected and 0xFFFFFF) == (argb and 0xFFFFFF)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        BorderStroke(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        CircleShape
                    )
                    .clickable { onSelect((alpha shl 24) or (argb and 0xFFFFFF)) }
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .semantics { contentDescription = "custom" }
                .clickable { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Colorize, stringResource(R.string.custom_color), Modifier.size(18.dp))
        }
    }
    if (showDialog) {
        HexDialog(selected, onDismiss = { showDialog = false }) {
            onSelect((alpha shl 24) or (it and 0xFFFFFF))
            showDialog = false
        }
    }
}

@Composable
private fun HexDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val parsed = text.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hex_color)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(7).uppercase() },
                singleLine = true,
                prefix = { Text("#") },
                isError = parsed == null,
                modifier = Modifier.padding(top = 4.dp)
            )
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                onConfirm(parsed ?: 0)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
