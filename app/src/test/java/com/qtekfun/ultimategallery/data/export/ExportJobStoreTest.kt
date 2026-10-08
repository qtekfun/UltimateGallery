package com.qtekfun.ultimategallery.data.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportJobStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = ExportJobStore(context)

    @Test
    fun jobRoundTrips() {
        val job = ExportJob(listOf(5, 1, 9_000_000_000L), WatermarkProfile(source = WatermarkSource.Text("x")))
        store.writeJob("job-a", job)
        assertEquals(job, store.readJob("job-a"))
        assertNull(store.readJob("nope"))
    }

    @Test
    fun resultRoundTripsIncludingFailuresAndCancelFlag() {
        val result = ExportResult(
            "job-b",
            listOf(
                ExportItemResult(1, Uri.parse("content://media/external/images/media/7"), "a_wm.jpg", null, true),
                ExportItemResult(2, null, null, "boom")
            ),
            cancelled = true
        )
        store.writeResult(result)
        assertEquals(result, store.readResult("job-b"))
        assertTrue(store.hasResult("job-b"))
        assertFalse(store.hasResult("job-zzz"))
    }

    @Test
    fun deletesOnlyOldFiles() {
        store.writeJob("old", ExportJob(listOf(1), WatermarkProfile()))
        store.writeJob("new", ExportJob(listOf(1), WatermarkProfile()))
        File(context.filesDir, "export-jobs/old.json").setLastModified(1_000L)
        store.deleteOlderThan(System.currentTimeMillis() - 1_000_000L)
        assertNull(store.readJob("old"))
        assertEquals(listOf(1L), store.readJob("new")?.itemIds)
    }
}
