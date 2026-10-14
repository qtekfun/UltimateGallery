package com.qtekfun.ultimategallery.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.data.db.DeviceHiddenFolderEntity
import com.qtekfun.ultimategallery.feature.gallery.appFolderLabel
import com.qtekfun.ultimategallery.feature.gallery.errorMessage
import com.qtekfun.ultimategallery.feature.gallery.rememberAllFilesAccessGate
import kotlinx.coroutines.launch

/** One entry of the settings home. */
class SettingsEntry(val icon: ImageVector, val title: Int, val summary: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } }
            )
        },
        content = content
    )
}

/** The settings home: a list of sections. */
@Composable
fun SettingsScreen(entries: List<SettingsEntry>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsScaffold(stringResource(R.string.settings_title), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(entries) { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = entry.onClick).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(entry.summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Settings → Folders: the folders hidden on the whole device, with a way back, then every other
 * folder with a switch to show or hide it in this app.
 */
@Composable
fun FoldersSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val states by viewModel.folderStates.collectAsStateWithLifecycle()
    val onDevice by viewModel.deviceHidden.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val withAllFilesAccess = rememberAllFilesAccessGate(viewModel::hasAllFilesAccess) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.all_files_access_denied)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.deviceShowEvents.collect { event ->
            snackbar.showSnackbar(event.result.errorMessage()?.let(resources::getString) ?: resources.getString(R.string.folder_shown_on_device, event.name))
        }
    }
    SettingsScaffold(stringResource(R.string.settings_folders), onBack, modifier, snackbar) { padding ->
        val list = states
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() && onDevice.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.folders_empty))
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                if (onDevice.isNotEmpty()) {
                    item { SectionHeader(R.string.folders_hidden_on_device, R.string.folders_hidden_on_device_note) }
                    items(onDevice, key = { "device:" + it.path }) { folder ->
                        DeviceHiddenRow(folder, onShow = { withAllFilesAccess { viewModel.showOnDevice(folder) } })
                    }
                    item { SectionHeader(R.string.folders_in_app, R.string.folders_hidden_note) }
                } else {
                    item { Note(R.string.folders_hidden_note) }
                }
                items(list, key = { it.folder.bucketId }) { state ->
                    val folder = state.folder
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MediaThumb(folder.coverUri, contentDescription = null, modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (folder.relativePath ?: "") + "  ·  " + pluralStringResource(R.plurals.item_count, folder.count, folder.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            state.hiddenByApp?.let {
                                Text(
                                    stringResource(R.string.folder_hidden_by_app, appFolderLabel(it)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Switch(
                            checked = !state.hidden,
                            enabled = state.hiddenByApp == null,
                            onCheckedChange = { viewModel.setFolderVisible(state, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionHeader(title: Int, note: Int) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() }
        )
        Note(note)
    }
}

@Composable
private fun DeviceHiddenRow(folder: DeviceHiddenFolderEntity, onShow: () -> Unit) {
    val label = stringResource(R.string.folder_show_on_device_label, folder.name)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                folder.relativePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(onClick = onShow, modifier = Modifier.semantics { contentDescription = label }) {
            Text(stringResource(R.string.folder_show_on_device))
        }
    }
}

/** Settings → App folders: choose which app-generated folders are hidden automatically. */
@Composable
fun AppFoldersSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val rows by viewModel.appFolders.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_app_folders), onBack, modifier) { padding ->
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text(stringResource(R.string.app_folders_none)) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item {
                    Text(
                        stringResource(R.string.app_folders_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                items(list, key = { it.detected.entry.id }) { row ->
                    val count = row.detected.folders.sumOf { it.count }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(appFolderLabel(row.detected.entry), style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.item_count, count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = row.autoHidden, onCheckedChange = { viewModel.setAppFolderHidden(row.detected.entry.id, it) })
                    }
                }
            }
        }
    }
}

/** The sections of the settings home. */
fun settingsEntries(
    onAppearance: () -> Unit,
    onFolders: () -> Unit,
    onAppFolders: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit
): List<SettingsEntry> = listOf(
    SettingsEntry(Icons.Outlined.Palette, R.string.settings_appearance, R.string.settings_appearance_summary, onAppearance),
    SettingsEntry(Icons.Outlined.FolderOpen, R.string.settings_folders, R.string.settings_folders_summary, onFolders),
    SettingsEntry(Icons.Outlined.Apps, R.string.settings_app_folders, R.string.settings_app_folders_summary, onAppFolders),
    SettingsEntry(Icons.Outlined.Crop, R.string.settings_edit, R.string.settings_edit_summary, onEdit),
    SettingsEntry(Icons.Outlined.IosShare, R.string.settings_export, R.string.settings_export_summary, onExport),
    SettingsEntry(Icons.Outlined.Backup, R.string.settings_backup, R.string.settings_backup_summary, onBackup),
    SettingsEntry(Icons.Outlined.Info, R.string.settings_about, R.string.settings_about_summary, onAbout)
)
