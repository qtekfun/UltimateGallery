package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile

/** Callbacks of the profile sheet. */
class ProfileActions(
    val onSelect: (Long) -> Unit,
    val onSave: () -> Unit,
    val onSaveAs: (String) -> Unit,
    val onRename: (Long, String) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onDelete: (Long) -> Unit
)

private sealed interface Dialog {
    data object SaveAs : Dialog

    data class Rename(val profile: WatermarkProfile) : Dialog

    data class Delete(val profile: WatermarkProfile) : Dialog
}

/** Choose, save, rename, duplicate and delete watermark profiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(profiles: List<WatermarkProfile>, current: WatermarkProfile, dirty: Boolean, actions: ProfileActions, onDismiss: () -> Unit) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.profile_menu),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(profile, selected = profile.id == current.id, actions = actions, onDialog = { dialog = it }, onPicked = onDismiss)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    actions.onSave()
                    onDismiss()
                }, enabled = dirty && current.id != 0L) {
                    Text(stringResource(R.string.profile_save))
                }
                OutlinedButton(onClick = { dialog = Dialog.SaveAs }) { Text(stringResource(R.string.profile_save_as)) }
            }
        }
    }
    when (val d = dialog) {
        Dialog.SaveAs -> NameDialog(
            title = stringResource(R.string.profile_save_as),
            initial = "",
            onDismiss = { dialog = null },
            onConfirm = {
                actions.onSaveAs(it)
                dialog = null
                onDismiss()
            }
        )
        is Dialog.Rename -> NameDialog(
            title = stringResource(R.string.profile_rename),
            initial = d.profile.name,
            onDismiss = { dialog = null },
            onConfirm = {
                actions.onRename(d.profile.id, it)
                dialog = null
            }
        )
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(d.profile.id)
                    dialog = null
                }) { Text(stringResource(R.string.profile_delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
        null -> Unit
    }
}

@Composable
private fun ProfileRow(profile: WatermarkProfile, selected: Boolean, actions: ProfileActions, onDialog: (Dialog) -> Unit, onPicked: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable {
            actions.onSelect(profile.id)
            onPicked()
        }.padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(profile.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.profile_more))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.profile_rename)) }, onClick = {
                    menu = false
                    onDialog(Dialog.Rename(profile))
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.profile_duplicate)) }, onClick = {
                    menu = false
                    actions.onDuplicate(profile.id)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.profile_delete)) }, onClick = {
                    menu = false
                    onDialog(Dialog.Delete(profile))
                })
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
