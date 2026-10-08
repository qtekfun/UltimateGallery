package com.qtekfun.ultimategallery.feature.settings

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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.feature.gallery.appFolderLabel

/** One entry of the settings home. */
class SettingsEntry(val icon: ImageVector, val title: Int, val summary: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        modifier = modifier,
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

/** Settings → Folders: every folder with a switch to show or hide it in this app. */
@Composable
fun FoldersSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val states by viewModel.folderStates.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_folders), onBack, modifier) { padding ->
        val list = states
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text(stringResource(R.string.folders_empty)) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item {
                    Text(
                        stringResource(R.string.folders_hidden_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
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

/** The sections available now; later phases add more. */
fun settingsEntries(onFolders: () -> Unit, onAppFolders: () -> Unit): List<SettingsEntry> = listOf(
    SettingsEntry(Icons.Outlined.FolderOpen, R.string.settings_folders, R.string.settings_folders_summary, onFolders),
    SettingsEntry(Icons.Outlined.Apps, R.string.settings_app_folders, R.string.settings_app_folders_summary, onAppFolders)
)
