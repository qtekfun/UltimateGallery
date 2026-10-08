package com.qtekfun.ultimategallery.data.export

import android.content.Context
import android.net.Uri
import com.qtekfun.ultimategallery.data.profile.ProfileCodec
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** What a worker needs to run an export job. */
data class ExportJob(val itemIds: List<Long>, val profile: WatermarkProfile)

/**
 * Job and result files under `filesDir/export-jobs`. Jobs travel as files because WorkManager's
 * input data is limited to 10 KB; only the job id is passed in the work request.
 */
@Singleton
class ExportJobStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun writeJob(jobId: String, job: ExportJob) {
        val json = JSONObject().apply {
            put("ids", JSONArray(job.itemIds))
            put("profile", JSONObject(ProfileCodec.toJson(job.profile)))
        }
        jobFile(jobId).writeText(json.toString())
    }

    fun readJob(jobId: String): ExportJob? = runCatching {
        val json = JSONObject(jobFile(jobId).readText())
        val ids = json.getJSONArray("ids")
        ExportJob(List(ids.length()) { ids.getLong(it) }, ProfileCodec.fromJson(json.getJSONObject("profile").toString()))
    }.getOrNull()

    fun writeResult(result: ExportResult) {
        resultFile(result.jobId).writeText(resultToJson(result).toString())
    }

    fun readResult(jobId: String): ExportResult? = runCatching { resultFromJson(JSONObject(resultFile(jobId).readText())) }.getOrNull()

    fun hasResult(jobId: String): Boolean = resultFile(jobId).exists()

    /** Deletes job and result files last modified before [olderThanMs] (epoch millis). */
    fun deleteOlderThan(olderThanMs: Long) {
        dir.listFiles()?.filter { it.lastModified() < olderThanMs }?.forEach { it.delete() }
    }

    private fun jobFile(jobId: String) = File(dir, "$jobId.json")

    private fun resultFile(jobId: String) = File(dir, "$jobId.result.json")

    companion object {
        const val DIR_NAME = "export-jobs"

        fun resultToJson(result: ExportResult): JSONObject = JSONObject().apply {
            put("jobId", result.jobId)
            put("cancelled", result.cancelled)
            put(
                "items",
                JSONArray().apply {
                    result.items.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("sourceId", item.sourceId)
                                put("outputUri", item.outputUri?.toString() ?: JSONObject.NULL)
                                put("outputName", item.outputName ?: JSONObject.NULL)
                                put("error", item.error ?: JSONObject.NULL)
                                put("downscaled", item.downscaled)
                            }
                        )
                    }
                }
            )
        }

        fun resultFromJson(json: JSONObject): ExportResult {
            val items = json.getJSONArray("items")
            return ExportResult(
                jobId = json.getString("jobId"),
                items = List(items.length()) { i ->
                    val o = items.getJSONObject(i)
                    ExportItemResult(
                        sourceId = o.getLong("sourceId"),
                        outputUri = o.optStringOrNull("outputUri")?.let(Uri::parse),
                        outputName = o.optStringOrNull("outputName"),
                        error = o.optStringOrNull("error"),
                        downscaled = o.optBoolean("downscaled", false)
                    )
                },
                cancelled = json.optBoolean("cancelled", false)
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    }
}
