package com.qtekfun.ultimategallery.feature.watermark

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.export.ExportPaths

/** The watermark editor: canvas, batch strip and the control panels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkEditorScreen(
    onBack: () -> Unit,
    onExportStarted: (jobId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatermarkEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var panelOpen by remember { mutableStateOf(true) }
    var showProfiles by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    LaunchedEffect(viewModel) {
        viewModel.snapEvents.collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    val importFailed = stringResource(R.string.logo_import_failed)
    val empty = stringResource(R.string.empty_selection)
    val profileSaved = stringResource(R.string.profile_saved)
    val lastProfile = stringResource(R.string.profile_last)
    LaunchedEffect(state.message) {
        when (state.message) {
            EditorMessage.LOGO_IMPORT_FAILED -> snackbar.showSnackbar(importFailed)
            EditorMessage.EMPTY_SELECTION -> snackbar.showSnackbar(empty)
            EditorMessage.PROFILE_SAVED -> snackbar.showSnackbar(profileSaved)
            EditorMessage.LAST_PROFILE -> snackbar.showSnackbar(lastProfile)
            null -> Unit
        }
        if (state.message != null) viewModel.consumeMessage()
    }

    val startExport = {
        viewModel.startExport()?.let(onExportStarted)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startExport() }
    val beginExport = {
        if (state.items.isNotEmpty()) {
            val needsAsk = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsAsk && ExportPaths.isValidDestination(state.profile.export.destination)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startExport()
            }
        }
    }

    val actions = remember(viewModel) {
        EditorActions(
            onSetType = viewModel::setType,
            onSetBase = viewModel::setBase,
            onText = viewModel::setText,
            onStyle = viewModel::setTextStyle,
            onSpacing = { viewModel.setTiling(spacing = it) },
            onStagger = { viewModel.setTiling(staggerX = it) },
            onImportLogo = viewModel::importLogo,
            hasLogo = viewModel::hasLogo,
            lastText = viewModel::lastText,
            lastImage = viewModel::lastImage,
            onOpacity = viewModel::setOpacity,
            onMargin = viewModel::setMargin,
            onSnap = viewModel::setSnapEnabled,
            onResetPlacement = viewModel::resetPlacement
        )
    }

    if (showProfiles) {
        ProfileSheet(
            profiles = state.profiles,
            current = state.profile,
            dirty = state.dirty,
            actions = ProfileActions(
                onSelect = viewModel::selectProfile,
                onSave = viewModel::saveProfile,
                onSaveAs = viewModel::saveProfileAs,
                onRename = viewModel::renameProfile,
                onDuplicate = viewModel::duplicateProfile,
                onDelete = viewModel::deleteProfile
            ),
            onDismiss = { showProfiles = false }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.discard_keep)) } }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AssistChip(
                        onClick = { showProfiles = true },
                        label = { Text(state.profile.name + if (state.dirty) " •" else "", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Bookmarks, contentDescription = stringResource(R.string.profile_menu)) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo))
                    }
                    FilledTonalButton(onClick = beginExport, enabled = state.items.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.export_action))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val current = state.current
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> CircularProgressIndicator()
                    current != null -> EditorCanvas(
                        item = current,
                        profile = state.profile,
                        orientation = state.orientation,
                        renderer = viewModel.renderer,
                        guideX = state.guideX,
                        guideY = state.guideY,
                        onGestureStart = viewModel::gestureStart,
                        onGesture = viewModel::gesture,
                        onGestureEnd = viewModel::gestureEnd,
                        onHandleDrag = viewModel::handleDrag,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (state.items.size > 1 || current != null) {
                BatchStrip(
                    items = state.items,
                    selectedIndex = state.index,
                    profile = state.profile,
                    renderer = viewModel.renderer,
                    onSelect = viewModel::select,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                    bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)
                )
            ) {
                Column(Modifier.navigationBarsPadding().animateContentSize()) {
                    PrimaryTabRow(
                        selectedTabIndex = state.tab.ordinal,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        EditorTab.entries.forEach { tab ->
                            Tab(
                                selected = state.tab == tab,
                                onClick = {
                                    if (state.tab ==
                                        tab
                                    ) {
                                        panelOpen = !panelOpen
                                    } else {
                                        viewModel.setTab(tab)
                                        panelOpen = true
                                    }
                                },
                                text = { Text(stringResource(tab.label())) }
                            )
                        }
                    }
                    AnimatedVisibility(visible = panelOpen) {
                        val panelModifier = Modifier.fillMaxWidth().heightIn(
                            max = 230.dp
                        ).padding(horizontal = 16.dp, vertical = 12.dp)
                        when (state.tab) {
                            EditorTab.MARK -> MarkPanel(state.profile, actions, panelModifier)
                            EditorTab.STYLE -> StylePanel(state.profile, actions, panelModifier)
                            EditorTab.PLACEMENT -> PlacementPanel(
                                state.profile,
                                state.orientation,
                                state.snapEnabled,
                                actions,
                                panelModifier
                            )
                            EditorTab.EXPORT -> ExportPanel(state.profile.export, viewModel::setExportSettings, panelModifier)
                        }
                    }
                }
            }
        }
    }
}

private fun EditorTab.label(): Int = when (this) {
    EditorTab.MARK -> R.string.tab_mark
    EditorTab.STYLE -> R.string.tab_style
    EditorTab.PLACEMENT -> R.string.tab_placement
    EditorTab.EXPORT -> R.string.tab_export
}
