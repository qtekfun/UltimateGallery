package com.qtekfun.ultimategallery.feature.viewer

import android.content.res.Resources
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Degrees the preview is turned, animated with a spring. */
@Composable
internal fun animatedPreviewDegrees(turns: Int): Float {
    val degrees by animateFloatAsState(
        targetValue = turns * 90f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "videoRotatePreview"
    )
    return degrees
}

/**
 * Scale that keeps a page of [width] x [height] fully visible while turned by [degrees]: 1 when upright,
 * min(w/h, h/w) at a quarter turn, blended smoothly in between.
 */
internal fun fitScaleForRotation(width: Float, height: Float, degrees: Float): Float {
    if (width <= 0f || height <= 0f) return 1f
    val quarter = min(width / height, height / width)
    val t = abs(sin(Math.toRadians(degrees.toDouble()))).toFloat()
    return 1f + (quarter - 1f) * t
}

/** The compact bottom panel of the rotate preview. */
@Composable
internal fun VideoRotatePanel(state: VideoRotateState, onTurn: (Int) -> Unit, onCancel: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val degrees = Math.floorMod(state.turns, 4) * 90
    val previewState = stringResource(R.string.video_rotate_preview_state, degrees)
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (state.saving) {
            val savingText = stringResource(R.string.video_rotate_saving)
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = savingText })
        }
        Row(
            Modifier.fillMaxWidth().semantics { stateDescription = previewState },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTurn(-1)
                },
                enabled = !state.saving
            ) { Icon(Icons.AutoMirrored.Filled.RotateLeft, stringResource(R.string.video_rotate_left)) }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTurn(1)
                },
                enabled = !state.saving
            ) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.video_rotate_right)) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.video_rotate_cancel), color = Color.White)
                }
                Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.video_rotate_save))
                }
            }
        }
    }
}

/** The "how do you want to save" dialog, the same pattern as the photo editor's. */
@Composable
internal fun VideoRotateAskDialog(onChoose: (SaveBehavior, Boolean) -> Unit, onDismiss: () -> Unit) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_rotate_ask_title)) },
        text = {
            Column {
                Text(stringResource(R.string.video_rotate_ask_message))
                TextButton(onClick = { onChoose(SaveBehavior.OVERWRITE, rememberChoice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.video_rotate_overwrite), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = rememberChoice, role = Role.Checkbox, onValueChange = { rememberChoice = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = null)
                    Text(stringResource(R.string.video_rotate_remember), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(SaveBehavior.COPY, rememberChoice) }) { Text(stringResource(R.string.video_rotate_save_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.video_rotate_cancel)) } }
    )
}

/** The panel plus the ask dialog, driven by a [VideoRotateController]. */
@Composable
internal fun VideoRotateOverlay(state: VideoRotateState, controller: VideoRotateController, modifier: Modifier = Modifier) {
    VideoRotatePanel(state, controller::turn, controller::cancel, controller::save, modifier)
    if (state.asking) VideoRotateAskDialog(controller::confirmAsk, controller::dismissAsk)
}

/** The snackbar text for an outcome. */
internal fun rotateMessage(resources: Resources, event: VideoRotateEvent): String = when (event) {
    is VideoRotateEvent.Rotated ->
        resources.getString(if (event.overwritten) R.string.video_rotate_done else R.string.video_rotate_done_copy)
    VideoRotateEvent.Denied -> resources.getString(R.string.video_rotate_denied)
    VideoRotateEvent.Unsupported -> resources.getString(R.string.video_rotate_unsupported)
    is VideoRotateEvent.Failed -> resources.getString(R.string.video_rotate_failed, event.message)
}
