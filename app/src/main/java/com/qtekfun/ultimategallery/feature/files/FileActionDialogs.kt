package com.qtekfun.ultimategallery.feature.files

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.media.Destinations
import com.qtekfun.ultimategallery.data.media.FileNames
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem

/** Renders whichever dialog [viewModel] currently asks for. Put it once on each screen that offers file actions. */
@Composable
fun FileActionDialogs(viewModel: FileActionsViewModel) {
    val dialog by viewModel.dialog.collectAsState()
    val folders by viewModel.folders.collectAsState()
    when (val current = dialog) {
        FileDialog.None -> Unit

        is FileDialog.ChooseDestination -> DestinationPickerDialog(
            mode = current.mode,
            items = current.items,
            folders = folders,
            onChoose = viewModel::confirmDestination,
            onDismiss = viewModel::dismissDialog
        )

        is FileDialog.Rename -> RenameDialog(
            currentName = current.item.displayName,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissDialog
        )

        is FileDialog.InfoMulti -> MultiInfoDialog(items = current.items, onDismiss = viewModel::dismissDialog)
    }
}

/** Lists the existing folders valid for [items] and a field to create a new folder under Pictures/. */
@Composable
fun DestinationPickerDialog(mode: DestinationMode, items: List<MediaItem>, folders: List<Folder>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (mode == DestinationMode.MOVE) R.string.files_move_title else R.string.files_copy_title)) },
        text = { DestinationPickerContent(mode, items, folders, onChoose) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The body of [DestinationPickerDialog], also usable on its own. */
@Composable
fun DestinationPickerContent(mode: DestinationMode, items: List<MediaItem>, folders: List<Folder>, onChoose: (String) -> Unit) {
    val names = remember(folders) { folders.filter { it.relativePath != null }.associate { Destinations.normalize(it.relativePath.orEmpty()) to it.name } }
    val paths =
        remember(folders, items) { Destinations.suggestions(folders, forVideo = items.any { it.isVideo }).filter { Destinations.isValidFor(it, items) } }
    var newName by rememberSaveable { mutableStateOf("") }
    val showError = newName.isNotEmpty() && !Destinations.isValidFolderName(newName)
    val newPath = Destinations.newFolderPath(Destinations.NEW_FOLDER_ROOT, newName)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (paths.isEmpty()) {
            Text(stringResource(R.string.files_no_folders), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(paths, key = { it }) { path ->
                    Column(Modifier.fillMaxWidth().clickable { onChoose(path) }.padding(vertical = 8.dp)) {
                        Text(names[path] ?: path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge)
                        Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text(stringResource(R.string.files_new_folder_label)) },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.files_new_folder_error)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { newPath?.let(onChoose) }, enabled = newPath != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (mode == DestinationMode.MOVE) R.string.files_new_folder_move else R.string.files_new_folder_copy))
        }
    }
}

/** Edits the file name without its extension, which stays fixed and is shown beside the field. */
@Composable
fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_rename_title)) },
        text = { RenameContent(currentName, onConfirm) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The body of [RenameDialog]: the name field and the Rename button. */
@Composable
fun RenameContent(currentName: String, onConfirm: (String) -> Unit) {
    val extension = remember(currentName) { FileNames.extension(currentName) }
    var name by rememberSaveable(currentName) { mutableStateOf(FileNames.baseName(currentName)) }
    val valid = FileNames.sanitize(name).isNotEmpty() && name.trim() == FileNames.sanitize(name)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.files_rename_label)) },
            singleLine = true,
            isError = !valid,
            supportingText = {
                if (!valid) {
                    Text(stringResource(R.string.files_rename_error))
                } else if (extension.isNotEmpty()) {
                    Text(stringResource(R.string.files_rename_extension, extension))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onConfirm(name) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.files_rename_action))
        }
    }
}

/** Count and total size of a multi-selection; a single item uses the viewer's info panel instead. */
@Composable
fun MultiInfoDialog(items: List<MediaItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val total = items.sumOf { it.sizeBytes }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.files_info_count, items.size, items.size), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.files_info_total_size, Formatter.formatFileSize(context, total)), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_close)) } }
    )
}

/** Two buttons for the viewer's info panel (R38). */
@Composable
fun InfoMetadataActions(onShareWithout: () -> Unit, onSaveCopyWithout: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onShareWithout, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.files_share_no_metadata)) }
        OutlinedButton(onClick = onSaveCopyWithout, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.files_save_no_metadata)) }
    }
}

/** Turns [FileActionsViewModel.events] into snackbars (with Undo after a trash) and launches the share sheet. */
@Composable
fun FileActionsEffects(viewModel: FileActionsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            val res = context.resources
            when (event) {
                is FileEvent.Trashed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = res.getQuantityString(R.plurals.files_trashed, event.count, event.count),
                        actionLabel = res.getString(R.string.files_undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash(event.items)
                }

                is FileEvent.Moved -> snackbarHostState.showSnackbar(res.getQuantityString(R.plurals.files_moved, event.count, event.count))

                is FileEvent.Copied -> snackbarHostState.showSnackbar(res.getQuantityString(R.plurals.files_copied, event.count, event.count))

                FileEvent.Renamed -> snackbarHostState.showSnackbar(res.getString(R.string.files_renamed))

                FileEvent.CopySaved -> snackbarHostState.showSnackbar(res.getString(R.string.files_copy_saved_no_metadata))

                FileEvent.Unsupported -> snackbarHostState.showSnackbar(res.getString(R.string.files_no_metadata_unsupported))

                FileEvent.Failed -> snackbarHostState.showSnackbar(res.getString(R.string.files_failed))

                FileEvent.Denied -> snackbarHostState.showSnackbar(res.getString(R.string.files_denied))

                is FileEvent.ShareReady -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, res.getString(R.string.files_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
