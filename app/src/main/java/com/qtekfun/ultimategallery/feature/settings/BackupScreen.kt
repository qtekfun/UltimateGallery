package com.qtekfun.ultimategallery.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.backup.BackupCodec
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val JSON_MIME = "application/json"

/** Settings → Backup: export the configuration to a file, or import one. Photos are never involved. */
@Composable
fun BackupSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BackupEvent.Exported -> if (event.profilesWithoutImage > 0) {
                    resources.getQuantityString(R.plurals.backup_exported_images, event.profilesWithoutImage, event.profilesWithoutImage)
                } else {
                    resources.getString(R.string.backup_exported)
                }
                is BackupEvent.Imported -> if (event.profilesAdded > 0) {
                    resources.getQuantityString(R.plurals.backup_imported_profiles, event.profilesAdded, event.profilesAdded)
                } else {
                    resources.getString(R.string.backup_imported)
                }
                BackupEvent.Invalid -> resources.getString(R.string.backup_invalid)
                BackupEvent.Failed -> resources.getString(R.string.backup_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }
    SettingsScaffold(stringResource(R.string.settings_backup), onBack, modifier, snackbar) { padding ->
        Column(Modifier.padding(padding)) {
            BackupAction(
                Icons.Outlined.FileUpload,
                R.string.backup_export,
                R.string.backup_export_summary
            ) { exportLauncher.launch("ultimategallery-settings-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json") }
            BackupAction(Icons.Outlined.FileDownload, R.string.backup_import, R.string.backup_import_summary) {
                importLauncher.launch(arrayOf(JSON_MIME, "text/plain", "application/octet-stream"))
            }
            Text(
                stringResource(R.string.backup_note),
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    pending?.let { ConfirmImportDialog(it, onConfirm = viewModel::confirmImport, onCancel = viewModel::cancelImport) }
}

@Composable
private fun BackupAction(icon: ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 20.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmImportDialog(contents: BackupCodec.Contents, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column {
                if (contents.settings != null) Text(stringResource(R.string.backup_import_body))
                if (contents.profiles.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.backup_import_profiles, contents.profiles.size, contents.profiles.size),
                        Modifier.padding(top = 8.dp)
                    )
                }
                if (contents.newerSchema) Text(stringResource(R.string.backup_import_newer), Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_import_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }
    )
}
