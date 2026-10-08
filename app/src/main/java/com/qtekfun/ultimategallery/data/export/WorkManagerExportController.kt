package com.qtekfun.ultimategallery.data.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.qtekfun.ultimategallery.data.export.ExportWorker.Companion.KEY_CURRENT_NAME
import com.qtekfun.ultimategallery.data.export.ExportWorker.Companion.KEY_DONE
import com.qtekfun.ultimategallery.data.export.ExportWorker.Companion.KEY_FRACTION
import com.qtekfun.ultimategallery.data.export.ExportWorker.Companion.KEY_JOB_ID
import com.qtekfun.ultimategallery.data.export.ExportWorker.Companion.KEY_TOTAL
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Runs exports as unique WorkManager jobs; see [ExportWorker]. */
@Singleton
class WorkManagerExportController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ExportJobStore,
    @IoDispatcher private val io: CoroutineDispatcher
) : ExportController {
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun start(itemIds: List<Long>, profile: WatermarkProfile): String {
        val jobId = UUID.randomUUID().toString()
        store.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(JOB_RETENTION_DAYS))
        store.writeJob(jobId, ExportJob(itemIds, profile))
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setId(workId(jobId))
            .addTag(tagOf(jobId))
            .setInputData(workDataOf(KEY_JOB_ID to jobId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(uniqueNameOf(jobId), ExistingWorkPolicy.KEEP, request)
        return jobId
    }

    override fun observe(jobId: String): Flow<ExportStatus> = workManager.getWorkInfoByIdFlow(workId(jobId))
        .map { info -> statusOf(jobId, info) }
        .distinctUntilChanged()
        .flowOn(io)

    override fun cancel(jobId: String) {
        workManager.cancelWorkById(workId(jobId))
    }

    private suspend fun statusOf(jobId: String, info: WorkInfo?): ExportStatus = when (info?.state) {
        null -> ExportStatus.Failed("unknown job")
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportStatus.Queued
        WorkInfo.State.RUNNING -> {
            val total = info.progress.getInt(KEY_TOTAL, -1)
            if (total < 0) {
                ExportStatus.Queued
            } else {
                ExportStatus.Running(
                    done = info.progress.getInt(KEY_DONE, 0),
                    total = total,
                    currentFraction = info.progress.getFloat(KEY_FRACTION, 0f),
                    currentName = info.progress.getString(KEY_CURRENT_NAME)
                )
            }
        }
        WorkInfo.State.SUCCEEDED -> store.readResult(jobId)?.let { result ->
            if (result.cancelled) ExportStatus.Cancelled(result) else ExportStatus.Finished(result)
        } ?: ExportStatus.Failed("result missing")
        WorkInfo.State.FAILED -> ExportStatus.Failed("export failed")
        WorkInfo.State.CANCELLED -> ExportStatus.Cancelled(awaitResult(jobId))
    }

    /** The worker writes its partial result while it is being stopped, a moment after the state flips. */
    private suspend fun awaitResult(jobId: String) = run {
        repeat(RESULT_POLLS) {
            if (store.hasResult(jobId)) return@run store.readResult(jobId)
            delay(RESULT_POLL_MS)
        }
        store.readResult(jobId)
    }

    companion object {
        private const val JOB_RETENTION_DAYS = 7L
        private const val RESULT_POLLS = 10
        private const val RESULT_POLL_MS = 300L

        /** Stable work request id of a job, so a job can be observed without storing anything else. */
        fun workId(jobId: String): UUID = UUID.nameUUIDFromBytes("ultimategallery-export-$jobId".toByteArray())

        fun tagOf(jobId: String) = "export-$jobId"

        fun uniqueNameOf(jobId: String) = "export-$jobId"
    }
}
