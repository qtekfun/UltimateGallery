package com.qtekfun.ultimategallery.feature.watermark

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R

/** The watermark editor: canvas, batch strip and the control panels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkEditorScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: WatermarkEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var panelOpen by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel) {
        viewModel.snapEvents.collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    val importFailed = stringResource(R.string.logo_import_failed)
    val empty = stringResource(R.string.empty_selection)
    LaunchedEffect(state.message) {
        when (state.message) {
            EditorMessage.LOGO_IMPORT_FAILED -> snackbar.showSnackbar(importFailed)
            EditorMessage.EMPTY_SELECTION -> snackbar.showSnackbar(empty)
            null -> Unit
        }
        if (state.message != null) viewModel.consumeMessage()
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
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
                        EditorTab.entries.filter { it != EditorTab.EXPORT }.forEach { tab ->
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
                            EditorTab.EXPORT -> Unit
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
