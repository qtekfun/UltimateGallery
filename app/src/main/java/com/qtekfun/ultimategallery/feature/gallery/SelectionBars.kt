package com.qtekfun.ultimategallery.feature.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R

/** Actions of the selection bar. A null action is not offered (not implemented or not applicable). */
class SelectionActions(
    val onWatermark: () -> Unit,
    val onShare: (() -> Unit)? = null,
    val onMove: (() -> Unit)? = null,
    val onCopy: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onInfo: (() -> Unit)? = null,
    val onRename: (() -> Unit)? = null
)

/** Bottom bar for a selection: the primary Watermark button, then the secondary actions. */
@Composable
fun SelectionBar(actions: SelectionActions, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = actions.onWatermark) {
                Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.watermark))
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                actions.onShare?.let { BarIcon(Icons.Outlined.Share, R.string.share, it) }
                actions.onMove?.let { BarIcon(Icons.Outlined.DriveFileMove, R.string.move, it) }
                actions.onCopy?.let { BarIcon(Icons.Outlined.ContentCopy, R.string.copy, it) }
                actions.onDelete?.let { BarIcon(Icons.Outlined.Delete, R.string.delete, it) }
                actions.onInfo?.let { BarIcon(Icons.Outlined.Info, R.string.info, it) }
                actions.onRename?.let { BarIcon(Icons.Outlined.DriveFileRenameOutline, R.string.rename, it) }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}
