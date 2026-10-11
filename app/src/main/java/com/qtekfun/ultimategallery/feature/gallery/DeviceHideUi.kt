package com.qtekfun.ultimategallery.feature.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.folders.DeviceHideResult
import com.qtekfun.ultimategallery.data.folders.allFilesAccessIntent

/** Asks before hiding [folderName] on the whole device, spelling out what a `.nomedia` does. */
@Composable
fun HideOnDeviceDialog(folderName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
        title = { Text(stringResource(R.string.hide_on_device_title, folderName)) },
        text = { Text(stringResource(R.string.hide_on_device_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.hide_on_device_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Runs actions that need all-files access. The returned function runs its action at once when the
 * access is already granted; otherwise it explains why the access is needed, opens the system
 * screen, and runs the action on return only if the user granted it ([onDenied] otherwise).
 */
@Composable
fun rememberAllFilesAccessGate(hasAccess: () -> Boolean, onDenied: () -> Unit): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explaining by remember { mutableStateOf(false) }
    val currentHasAccess by rememberUpdatedState(hasAccess)
    val currentOnDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pending
        pending = null
        if (currentHasAccess()) action?.invoke() else currentOnDenied()
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = {
                explaining = false
                pending = null
            },
            icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
            title = { Text(stringResource(R.string.all_files_access_title)) },
            text = { Text(stringResource(R.string.all_files_access_body)) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    launcher.launch(allFilesAccessIntent(context))
                }) { Text(stringResource(R.string.all_files_access_open)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explaining = false
                    pending = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    return remember {
        { action ->
            if (currentHasAccess()) {
                action()
            } else {
                pending = action
                explaining = true
            }
        }
    }
}

/** The message for a hide or show on the device that didn't go through, or null when it did. */
fun DeviceHideResult.errorMessage(): Int? = when (this) {
    DeviceHideResult.DONE -> null
    DeviceHideResult.NO_ACCESS -> R.string.all_files_access_denied
    DeviceHideResult.NOT_ALLOWED -> R.string.device_hide_not_allowed
    DeviceHideResult.NOT_FOUND -> R.string.device_hide_not_found
    DeviceHideResult.FAILED -> R.string.device_hide_failed
}
