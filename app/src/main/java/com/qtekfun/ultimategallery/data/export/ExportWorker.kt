package com.qtekfun.ultimategallery.data.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.render.export.ExportPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs an export job (see [ExportJobStore]) as a foreground worker. Up to two small photos are processed at
 * once; a photo above [SMALL_PHOTO_PIXELS] takes the whole budget so large images never run side by side.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
    private val pipeline: ExportPipeline,
    private val store: ExportJobStore
) : CoroutineWorker(appContext, params) {
    private data class Progress(val done: Int, val total: Int, val fraction: Float, val name: String?)

    private class Batch(val job: ExportJob, val total: Int, val done: AtomicInteger, val progress: MutableStateFlow<Progress>, val permits: Semaphore)

    private val jobId: String get() = inputData.getString(KEY_JOB_ID).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(Progress(0, 0, 0f, null))

    override suspend fun doWork(): Result {
        val job = store.readJob(jobId) ?: return Result.failure()
        val items = media.loadItems(job.itemIds).associateBy { it.id }
        val total = job.itemIds.size
        val slots = arrayOfNulls<ExportItemResult>(total)
        val progress = MutableStateFlow(Progress(0, total, 0f, null))
        try {
            runCatching { setForeground(foregroundInfo(progress.value)) }
            coroutineScope {
                val reporter = launch {
                    progress.collectLatest { p ->
                        setProgress(workDataOf(KEY_DONE to p.done, KEY_TOTAL to p.total, KEY_FRACTION to p.fraction, KEY_CURRENT_NAME to p.name))
                        runCatching { setForeground(foregroundInfo(p)) }
                    }
                }
                runItems(job, items, slots, progress)
                reporter.cancel()
            }
            store.writeResult(ExportResult(jobId, slots.filterNotNull(), cancelled = false))
        } catch (e: CancellationException) {
            withContext(NonCancellable) { store.writeResult(ExportResult(jobId, slots.filterNotNull(), cancelled = true)) }
            throw e
        }
        return Result.success()
    }

    private suspend fun runItems(job: ExportJob, items: Map<Long, MediaItem>, slots: Array<ExportItemResult?>, progress: MutableStateFlow<Progress>) =
        coroutineScope {
            val total = job.itemIds.size
            val permits = Semaphore(PARALLELISM)
            val bigGate = Mutex()
            val batch = Batch(job, total, AtomicInteger(0), progress, permits)
            job.itemIds.mapIndexed { index, id ->
                async {
                    val item = items[id]
                    if (item == null) {
                        slots[index] = ExportItemResult(id, null, null, "Photo not found")
                        progress.value = Progress(batch.done.incrementAndGet(), total, 0f, null)
                        return@async
                    }
                    val big = item.width.toLong() * item.height > SMALL_PHOTO_PIXELS
                    val result = if (big) {
                        bigGate.withLock {
                            permits.acquire()
                            permits.acquire()
                        }
                        runItem(batch, item, index, PARALLELISM)
                    } else {
                        permits.acquire()
                        runItem(batch, item, index, 1)
                    }
                    slots[index] = result
                }
            }.awaitAll()
        }

    private suspend fun runItem(batch: Batch, item: MediaItem, index: Int, held: Int): ExportItemResult {
        try {
            val result = pipeline.exportOne(item, batch.job.profile, index, batch.total) { fraction ->
                batch.progress.value = Progress(batch.done.get(), batch.total, fraction, item.displayName)
            }
            batch.progress.value = Progress(batch.done.incrementAndGet(), batch.total, 0f, item.displayName)
            return result
        } finally {
            repeat(held) { batch.permits.release() }
        }
    }

    private fun foregroundInfo(p: Progress): ForegroundInfo {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.export_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val cancel = WorkManager.getInstance(appContext).createCancelPendingIntent(id)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(appContext.getString(R.string.export_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(0, appContext.getString(R.string.export_cancel), cancel)
        if (p.total > 0) {
            builder.setContentText(appContext.getString(R.string.export_notification_progress, p.done, p.total))
            builder.setProgress(PROGRESS_MAX, ((p.done + p.fraction) / p.total * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_JOB_ID = "jobId"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FRACTION = "fraction"
        const val KEY_CURRENT_NAME = "name"

        private const val CHANNEL_ID = "export"
        private const val NOTIFICATION_ID = 4201
        private const val PROGRESS_MAX = 1000
        private const val PARALLELISM = 2
        private const val SMALL_PHOTO_PIXELS = 8_000_000L
    }
}
