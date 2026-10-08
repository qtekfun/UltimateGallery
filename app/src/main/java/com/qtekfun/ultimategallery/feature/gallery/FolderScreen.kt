package com.qtekfun.ultimategallery.feature.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.feature.files.FileActionDialogs
import com.qtekfun.ultimategallery.feature.files.FileActionsEffects
import com.qtekfun.ultimategallery.feature.files.FileActionsViewModel
import kotlinx.coroutines.launch

/**
 * The photos of one folder. In pick mode it is the second step of the watermark flow: every tap
 * toggles a photo and the bottom bar continues to the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onWatermark: () -> Unit,
    onShare: (List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick = viewModel.pickMode
    val selecting = pick || state.selection.isNotEmpty()
    val snackbar = remember { SnackbarHostState() }
    val files: FileActionsViewModel = hiltViewModel()
    val selectedItems = { state.items.filter { it.id in state.selection } }
    val scope = rememberCoroutineScope()
    val noVideos = stringResource(R.string.videos_not_watermarked)

    FileActionDialogs(files)
    FileActionsEffects(files, snackbar)
    BackHandler(enabled = !pick && state.selection.isNotEmpty()) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedContent(
                targetState = !pick && state.selection.isNotEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "folder-top-bar"
            ) { contextual ->
                if (contextual) {
                    TopAppBar(
                        title = {
                            Text(
                                pluralStringResource(
                                    R.plurals.selected_count,
                                    state.selection.size,
                                    state.selection.size
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.close))
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                if (pick) {
                                    pluralStringResource(
                                        R.plurals.selected_count,
                                        state.selection.size,
                                        state.selection.size
                                    )
                                } else {
                                    viewModel.name
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (pick) {
                                IconButton(onClick = viewModel::selectAll) {
                                    Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all))
                                }
                            }
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = state.selection.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                if (pick) {
                    PickBar(count = state.selection.size, onContinue = onWatermark)
                } else {
                    SelectionBar(
                        SelectionActions(
                            onWatermark = {
                                if (viewModel.commitSelectionToBatch() >
                                    0
                                ) {
                                    onWatermark()
                                } else {
                                    scope.launch { snackbar.showSnackbar(noVideos) }
                                }
                            },
                            onShare = { onShare(state.items.filter { it.id in state.selection }) },
                            onMove = { selectedItems().let { files.moveTo(it) } },
                            onCopy = { selectedItems().let { files.copyTo(it) } },
                            onDelete = {
                                selectedItems().let { files.trash(it) }
                                viewModel.clearSelection()
                            },
                            onInfo = { files.showInfo(selectedItems()) },
                            onRename = state.items.singleOrNull { it.id in state.selection }?.let { item -> { files.rename(item) } }
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> Text(
                    stringResource(R.string.empty_folder),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> PhotoGrid(
                    entries = state.entries,
                    items = state.items,
                    columns = state.columns,
                    selection = state.selection,
                    selectionMode = selecting,
                    onOpen = { onOpenMedia(it, state.items) },
                    onToggle = { viewModel.toggle(it.id) },
                    onSelectionChange = viewModel::setSelection,
                    onColumnsChange = viewModel::setColumns,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun PickBar(count: Int, onContinue: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                pluralStringResource(R.plurals.selected_count, count, count),
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onContinue) { Text(stringResource(R.string.picker_continue)) }
        }
    }
}
