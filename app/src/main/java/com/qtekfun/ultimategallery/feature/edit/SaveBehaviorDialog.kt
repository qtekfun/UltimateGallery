package com.qtekfun.ultimategallery.feature.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior

/** The "save a copy, overwrite or cancel" question of the editors, with an option to remember the answer. */
@Composable
internal fun SaveBehaviorDialog(onChoose: (SaveBehavior, Boolean) -> Unit, onDismiss: () -> Unit) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crop_ask_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crop_ask_message))
                TextButton(onClick = { onChoose(SaveBehavior.OVERWRITE, rememberChoice) }) {
                    Text(stringResource(R.string.crop_overwrite), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = rememberChoice, role = Role.Checkbox, onValueChange = { rememberChoice = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = null)
                    Text(stringResource(R.string.crop_remember), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(SaveBehavior.COPY, rememberChoice) }) { Text(stringResource(R.string.crop_save_copy)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crop_cancel)) } }
    )
}
