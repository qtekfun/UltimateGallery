package com.qtekfun.ultimategallery.domain.export

import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import kotlinx.coroutines.flow.Flow

/** The outcome for one photo of a batch. */
data class ExportItemResult(
    /** MediaStore id of the original. */
    val sourceId: Long,
    /** The new file, or null when the photo failed. */
    val outputUri: Uri?,
    val outputName: String?,
    /** Short human-readable reason when [outputUri] is null. */
    val error: String?,
    /** True when the photo was larger than the memory budget and was scaled down to fit. */
    val downscaled: Boolean = false
)

data class ExportResult(
    val jobId: String,
    val items: List<ExportItemResult>,
    val cancelled: Boolean
) {
    val succeeded: List<ExportItemResult> get() = items.filter { it.outputUri != null }
    val failed: List<ExportItemResult> get() = items.filter { it.outputUri == null }
}

/** Where an export job is. Observed by the progress screen. */
sealed interface ExportStatus {
    data object Queued : ExportStatus

    /**
     * [done] photos are finished out of [total]; [currentFraction] is the progress (0..1) of the photo
     * being processed, [currentName] its original file name.
     */
    data class Running(
        val done: Int,
        val total: Int,
        val currentFraction: Float,
        val currentName: String?
    ) : ExportStatus {
        val overall: Float get() = if (total == 0) 0f else (done + currentFraction) / total
    }

    data class Finished(val result: ExportResult) : ExportStatus

    /** The user cancelled; [partial] holds what had been written before. */
    data class Cancelled(val partial: ExportResult?) : ExportStatus

    data class Failed(val message: String) : ExportStatus
}

/**
 * Runs watermark exports in the background. Originals are never modified; results are new files
 * in the profile's destination folder.
 */
interface ExportController {
    /** Schedules an export of the photos with these MediaStore ids and returns the job id. */
    fun start(itemIds: List<Long>, profile: WatermarkProfile): String

    /** Status updates of a job, ending with a terminal state. Safe to collect after process death. */
    fun observe(jobId: String): Flow<ExportStatus>

    fun cancel(jobId: String)
}
