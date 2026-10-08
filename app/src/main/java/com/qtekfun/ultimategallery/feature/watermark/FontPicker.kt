package com.qtekfun.ultimategallery.feature.watermark

import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.watermark.FontInfo
import com.qtekfun.ultimategallery.domain.watermark.FontKind

/** MIME types the system picker offers for font files; many providers report fonts as plain binary. */
internal val FontMimeTypes = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream"
)

/** A Compose family drawing [id] with [typefaceOf], or the default family when the font is unknown. */
@Composable
internal fun rememberFontFamily(id: String, typefaceOf: (String) -> Typeface?): FontFamily = remember(id, typefaceOf) {
    typefaceOf(id)?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) } ?: FontFamily.Default
}

/** The font choice sheet: every font in its own typeface, an import button and delete for imported fonts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(fonts: List<FontInfo>, selectedId: String, sampleText: String, actions: EditorActions, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FontPickerContent(fonts, selectedId, sampleText, actions, onSelect, onDismiss)
    }
}

@Composable
internal fun FontPickerContent(
    fonts: List<FontInfo>,
    selectedId: String,
    sampleText: String,
    actions: EditorActions,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<FontInfo?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            actions.onImportFont(uri)
            onClose()
        }
    }
    val sample = sampleText.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: stringResource(R.string.font_sample)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.font_picker_title), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { picker.launch(FontMimeTypes) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Text(stringResource(R.string.font_import), Modifier.padding(start = 8.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(fonts, key = { it.id }) { font ->
                FontRow(font, font.id == selectedId, sample, actions.typefaceOf, {
                    onSelect(font.id)
                    onClose()
                }, { pendingRemoval = font })
            }
        }
    }
    pendingRemoval?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.font_remove_title)) },
            text = { Text(stringResource(R.string.font_remove_body, font.label)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    actions.onRemoveFont(font.id)
                }) { Text(stringResource(R.string.font_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.font_remove_cancel)) } }
        )
    }
}

@Composable
private fun FontRow(font: FontInfo, selected: Boolean, sample: String, typefaceOf: (String) -> Typeface?, onSelect: () -> Unit, onRemove: () -> Unit) {
    val family = rememberFontFamily(font.id, typefaceOf)
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(font.label, style = MaterialTheme.typography.titleMedium, fontFamily = family, maxLines = 1)
            Text(sample, style = MaterialTheme.typography.bodyMedium, fontFamily = family, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        if (font.kind == FontKind.IMPORTED) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.font_remove_named, font.label))
            }
        }
    }
}
