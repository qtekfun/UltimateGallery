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
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import com.qtekfun.ultimategallery.data.prefs.HomeViewMode
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.feature.onboarding.AccessActions
import com.qtekfun.ultimategallery.feature.onboarding.PartialAccessBanner

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
                is HomeState.Loaded -> FolderContent(s, padding.calculateBottomPadding(), onOpenFolder)
            }
        }
    }
}

@Composable
private fun FolderContent(state: HomeState.Loaded, bottomInset: androidx.compose.ui.unit.Dp, onOpen: (Folder) -> Unit) {
    if (state.folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.empty_folders), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomInset + 96.dp)
    if (state.mode == HomeViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.folders, key = { it.bucketId }) { FolderCard(it, onOpen) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(state.folders, key = { it.bucketId }) { FolderRow(it, onOpen) }
        }
    }
}

@Composable
fun FolderCard(
    folder: Folder,
    onClick: (Folder) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Folder) -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(onClick = { onClick(folder) }, onLongClick = onLongClick?.let { { it(folder) } })
    ) {
        MediaThumb(folder.coverUri, contentDescription = null, modifier = Modifier.fillMaxSize())
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
fun FolderRow(
    folder: Folder,
    onClick: (Folder) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Folder) -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { onClick(folder) }, onLongClick = onLongClick?.let { { it(folder) } })
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
    }
}
