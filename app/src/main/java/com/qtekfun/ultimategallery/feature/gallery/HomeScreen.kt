package com.qtekfun.ultimategallery.feature.gallery

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.data.folders.AppFolderCatalog
import com.qtekfun.ultimategallery.data.folders.AppFolderEntry
import com.qtekfun.ultimategallery.data.folders.DetectedAppFolders
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import com.qtekfun.ultimategallery.data.prefs.HomeViewMode
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.feature.onboarding.AccessActions
import com.qtekfun.ultimategallery.feature.onboarding.PartialAccessBanner
import kotlinx.coroutines.launch

const val MIN_FOLDER_COLUMNS = 1
const val MAX_FOLDER_COLUMNS = 4

/**
 * The folders home. In [pickMode] it is the first step of the watermark flow: choosing a folder to
 * pick photos from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    accessLevel: MediaAccessLevel,
    accessActions: AccessActions,
    pickMode: Boolean,
    onOpenFolder: (Folder) -> Unit,
    onStartWatermark: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val offer by viewModel.appFolderOffer.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenMessage = stringResource(R.string.folder_hidden, "%s")
    val undoLabel = stringResource(R.string.undo_action)
    val onHide: (Folder) -> Unit = { folder ->
        viewModel.hide(folder)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(hiddenMessage.replace("%s", folder.name), actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.unhide(folder.bucketId)
        }
    }
    if (!pickMode) {
        offer?.let { AppFoldersOfferSheet(it, onApply = viewModel::applyAppFolders, onDismiss = viewModel::dismissAppFolderOffer) }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(if (pickMode) R.string.picker_title else R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (pickMode) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (pickMode) {
                        IconButton(onClick = viewModel::toggleShowHidden) {
                            Icon(
                                if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                stringResource(if (showHidden) R.string.hide_hidden_folders else R.string.show_hidden_folders)
                            )
                        }
                    }
                    (state as? HomeState.Loaded)?.let { loaded ->
                        IconButton(onClick = { viewModel.toggleMode(loaded.mode) }) {
                            if (loaded.mode == HomeViewMode.GRID) {
                                Icon(Icons.Outlined.ViewList, stringResource(R.string.view_as_list))
                            } else {
                                Icon(Icons.Outlined.GridView, stringResource(R.string.view_as_grid))
                            }
                        }
                    }
                    if (!pickMode) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!pickMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.startPicking()
                        onStartWatermark()
                    },
                    icon = { Icon(Icons.Outlined.WaterDrop, contentDescription = null) },
                    text = { Text(stringResource(R.string.watermark)) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            if (accessLevel == MediaAccessLevel.PARTIAL) PartialAccessBanner(accessActions)
            when (val s = state) {
                HomeState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is HomeState.Loaded -> FolderContent(s, padding.calculateBottomPadding(), onOpenFolder, if (pickMode) null else onHide, viewModel::setColumns)
            }
        }
    }
}

@Composable
private fun FolderContent(
    state: HomeState.Loaded,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpen: (Folder) -> Unit,
    onHide: ((Folder) -> Unit)?,
    onColumnsChange: (Int) -> Unit
) {
    if (state.folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.empty_folders), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomInset + 96.dp)
    if (state.mode == HomeViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(rememberAdaptiveColumns(state.columns)),
            modifier = Modifier.fillMaxSize().pinchToResize(state.columns, MIN_FOLDER_COLUMNS, MAX_FOLDER_COLUMNS, onColumnsChange),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.folders, key = { it.bucketId }) { FolderCard(it, onOpen, onHide = onHide) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(state.folders, key = { it.bucketId }) { FolderRow(it, onOpen, onHide = onHide) }
        }
    }
}

@Composable
fun FolderCard(folder: Folder, onClick: (Folder) -> Unit, modifier: Modifier = Modifier, onHide: ((Folder) -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(onClick = { onClick(folder) }, onLongClick = onHide?.let { { menu = true } })
    ) {
        MediaThumb(folder.coverUri, contentDescription = null, modifier = Modifier.fillMaxSize())
        HideMenu(menu, onDismiss = { menu = false }) { onHide?.invoke(folder) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.item_count, folder.count, folder.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        if (folder.coverIsVideo) {
            Icon(
                Icons.Outlined.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp)
            )
        }
    }
}

@Composable
fun FolderRow(folder: Folder, onClick: (Folder) -> Unit, modifier: Modifier = Modifier, onHide: ((Folder) -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { onClick(folder) }, onLongClick = onHide?.let { { menu = true } })
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumb(
            folder.coverUri,
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(R.plurals.item_count, folder.count, folder.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HideMenu(menu, onDismiss = { menu = false }) { onHide?.invoke(folder) }
    }
}

@Composable
private fun HideMenu(expanded: Boolean, onDismiss: () -> Unit, onHide: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hide_folder)) },
            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
            onClick = {
                onDismiss()
                onHide()
            }
        )
    }
}

/** Shown once on first run: offers to hide the folders of chat and screenshot apps. WhatsApp starts checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFoldersOfferSheet(offer: List<DetectedAppFolders>, onApply: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var checked by remember(offer) { mutableStateOf(offer.map { it.entry.id }.filter { it == AppFolderCatalog.WHATSAPP }.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.app_folders_offer_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.app_folders_offer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            offer.forEach { detected ->
                val id = detected.entry.id
                Row(
                    Modifier.fillMaxWidth().clickable { checked = if (id in checked) checked - id else checked + id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = id in checked, onCheckedChange = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(appFolderLabel(detected.entry), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            pluralStringResource(R.plurals.item_count, detected.folders.sumOf { it.count }, detected.folders.sumOf { it.count }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_folders_offer_skip)) }
                Button(onClick = { onApply(checked) }) { Text(stringResource(R.string.app_folders_offer_apply)) }
            }
        }
    }
}

@Composable
fun appFolderLabel(entry: AppFolderEntry): String = entry.labelRes?.let { stringResource(it) } ?: entry.label
