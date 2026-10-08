package com.qtekfun.ultimategallery.feature.export

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.domain.export.ExportStatus

/** Progress of an export job, then its summary with "Open folder" and "Share all". */
@Suppress("UnusedContentLambdaTargetStateParameter")
@Composable
fun ExportProgressScreen(
    onDone: () -> Unit,
    onOpenFolder: (ExportFolder) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportProgressViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val cancelling by viewModel.cancelling.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val result = (status as? ExportStatus.Finished)?.result ?: (status as? ExportStatus.Cancelled)?.partial
    LaunchedEffect(result) { result?.let(viewModel::resolveFolder) }

    Surface(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = status::class,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.safeDrawingPadding(),
            label = "export-status"
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val s = status) {
                    ExportStatus.Queued -> Running(null, cancelling, viewModel::cancel)
                    is ExportStatus.Running -> Running(s, cancelling, viewModel::cancel)
                    is ExportStatus.Finished -> Summary(s.result, folder, false, onDone, onOpenFolder) { shareAll(context, s.result) }
                    is ExportStatus.Cancelled -> {
                        val partial = s.partial
                        if (partial != null) {
                            Summary(partial, folder, true, onDone, onOpenFolder) { shareAll(context, partial) }
                        } else {
                            Title(Icons.Outlined.ErrorOutline, stringResource(R.string.result_cancelled_title))
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onDone) { Text(stringResource(R.string.result_done)) }
                        }
                    }
                    is ExportStatus.Failed -> {
                        Title(Icons.Outlined.ErrorOutline, stringResource(R.string.result_failed_title))
                        Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDone) { Text(stringResource(R.string.result_done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Running(status: ExportStatus.Running?, cancelling: Boolean, onCancel: () -> Unit) {
    val overall = status?.overall ?: 0f
    Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(32.dp))
    CircularWavyProgressIndicator(progress = { overall }, modifier = Modifier.size(140.dp))
    Spacer(Modifier.height(24.dp))
    if (status == null) {
        Text(stringResource(R.string.progress_queued), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            stringResource(R.string.progress_count, (status.done + 1).coerceAtMost(status.total), status.total),
            style = MaterialTheme.typography.titleLarge
        )
        status.currentName?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { status.currentFraction }, modifier = Modifier.fillMaxWidth(0.7f))
    }
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onCancel, enabled = !cancelling) {
        Text(stringResource(if (cancelling) R.string.progress_cancelling else R.string.progress_cancel))
    }
}

@Composable
private fun Summary(
    result: ExportResult,
    folder: ExportFolder?,
    cancelled: Boolean,
    onDone: () -> Unit,
    onOpenFolder: (ExportFolder) -> Unit,
    onShare: () -> Unit
) {
    val ok = result.succeeded.size
    val failed = result.failed.size
    Title(
        if (cancelled || failed > 0) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
        stringResource(if (cancelled) R.string.result_cancelled_title else R.string.result_title)
    )
    Text(
        if (failed == 0) {
            pluralStringResource(R.plurals.result_summary_ok, ok, ok, folder?.name.orEmpty())
        } else {
            pluralStringResource(R.plurals.result_summary_partial, ok, ok, failed)
        },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
    if (result.items.any { it.downscaled }) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.result_downscaled_note),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (failed > 0) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.result_failures), style = MaterialTheme.typography.titleSmall)
        result.failed.take(MAX_LISTED_FAILURES).forEach {
            Text("#${it.sourceId}: ${it.error.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { folder?.let(onOpenFolder) }, enabled = folder != null) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Text(stringResource(R.string.result_open_folder), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onShare, enabled = ok > 0) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(stringResource(R.string.result_share_all), Modifier.padding(start = 8.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.result_done)) }
}

private const val MAX_LISTED_FAILURES = 5

private fun shareAll(context: android.content.Context, result: ExportResult) {
    val uris = result.succeeded.mapNotNull { it.outputUri }
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType("image/*")
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)).setType("image/*")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
