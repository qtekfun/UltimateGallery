package com.qtekfun.ultimategallery.feature.photoedit

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.edit.PhotoEditSaver
import com.qtekfun.ultimategallery.data.edit.SaveResult
import com.qtekfun.ultimategallery.data.edit.mediaItem
import com.qtekfun.ultimategallery.data.edit.quadrants
import com.qtekfun.ultimategallery.data.edit.writePng
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.edit.AdjustKind
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.EditPoint
import com.qtekfun.ultimategallery.domain.edit.MaskOp
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.watermark.FontImportResult
import com.qtekfun.ultimategallery.domain.watermark.FontInfo
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import com.qtekfun.ultimategallery.testing.TestStores
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoEditorViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val stores = TestStores(UnconfinedTestDispatcher())

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var item: MediaItem

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())

        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = flowOf(emptyList())

        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = listOf(item)
    }

    private val fonts = object : FontLibrary {
        override val fonts: Flow<List<FontInfo>> = flowOf(emptyList())

        override fun typeface(id: String): Typeface? = null

        override suspend fun import(uri: Uri): FontImportResult = FontImportResult.Invalid

        override suspend fun remove(id: String) = Unit
    }

    private class FakeSaver : PhotoEditSaver {
        var saved: Pair<PhotoEdit, SaveBehavior>? = null

        override suspend fun save(item: MediaItem, edit: PhotoEdit, behavior: SaveBehavior): SaveResult {
            saved = edit to behavior
            return SaveResult.Saved(Uri.parse("content://media/external/images/media/9"), overwritten = false)
        }
    }

    private val saver = FakeSaver()
    private lateinit var vm: PhotoEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(stores.dispatcher)
        item = mediaItem(writePng(quadrants(120, 80), File(folder.root, "shot.png")), "image/png", 120, 80, "shot.png")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun open(behavior: SaveBehavior = SaveBehavior.COPY) {
        val settings = stores.settings()
        stores.complete { settings.setSaveBehavior(behavior) }
        vm = stores.track(
            PhotoEditorViewModel(
                SavedStateHandle(mapOf("mediaId" to 7L)),
                media,
                settings,
                saver,
                ExportImageLoader(context),
                fonts,
                stores.dispatcher,
                stores.dispatcher
            )
        )
        stores.drain()
    }

    private val state get() = vm.state.value

    @Test
    fun loadsThePreviewAndRendersTheBaseOnChange() {
        open()
        val preview = requireNotNull(state.preview)
        assertEquals(120, preview.width)
        assertSame(preview, state.base)
        vm.setAdjust(AdjustKind.SATURATION, -1f)
        stores.drain()
        assertNotSame(preview, state.base)
        assertTrue(state.hasChanges)
        assertFalse(state.rendering)
    }

    @Test
    fun sliderDragsShareOneUndoStep() {
        open()
        vm.setAdjust(AdjustKind.BRIGHTNESS, 0.2f)
        vm.setAdjust(AdjustKind.BRIGHTNESS, 0.4f)
        vm.setAdjust(AdjustKind.CONTRAST, 0.5f)
        vm.undo()
        assertEquals(0.4f, state.edit.adjust.brightness, 0f)
        assertEquals(0f, state.edit.adjust.contrast, 0f)
        vm.undo()
        assertTrue(state.edit.isIdentity)
        assertFalse(state.canUndo)
        vm.redo()
        assertEquals(0.4f, state.edit.adjust.brightness, 0f)
    }

    @Test
    fun drawSelectMoveAndDelete() {
        open()
        vm.selectTool(EditorTool.DRAW)
        vm.finishStroke(listOf(EditPoint(0.2f, 0.5f), EditPoint(0.8f, 0.5f)))
        val stroke = state.edit.annotations.single() as Annotation.Stroke
        vm.setDrawMode(DrawMode.SELECT)
        assertEquals(stroke.id, vm.selectAt(EditPoint(0.5f, 0.5f), slopPx = 4f))
        vm.transformSelected(0.1f, 0f, 1f, 0f)
        vm.transformSelected(0.1f, 0f, 1f, 0f)
        vm.endTransform()
        val moved = state.edit.annotations.single() as Annotation.Stroke
        assertEquals(0.4f, moved.points.first().x, 1e-5f)
        vm.deleteSelected()
        assertTrue(state.edit.annotations.isEmpty())
        assertNull(state.selectedId)
        // Undo brings it back where it was moved to, then the move is one step.
        vm.undo()
        assertEquals(0.4f, (state.edit.annotations.single() as Annotation.Stroke).points.first().x, 1e-5f)
        vm.undo()
        assertEquals(0.2f, (state.edit.annotations.single() as Annotation.Stroke).points.first().x, 1e-5f)
    }

    @Test
    fun aTapIsNotAShape() {
        open()
        vm.selectTool(EditorTool.DRAW)
        vm.setDrawMode(DrawMode.RECTANGLE)
        vm.finishShape(EditPoint(0.5f, 0.5f), EditPoint(0.501f, 0.5f))
        assertTrue(state.edit.annotations.isEmpty())
        vm.finishShape(EditPoint(0.2f, 0.2f), EditPoint(0.6f, 0.7f))
        assertEquals(1, state.edit.annotations.size)
    }

    @Test
    fun textIsAddedSelectedAndRemovedWhenEmptied() {
        open()
        vm.selectTool(EditorTool.TEXT)
        vm.addText("Hello")
        val text = state.edit.annotations.single() as Annotation.Text
        assertEquals(text.id, state.selectedId)
        vm.setTextColor(0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), (state.edit.annotations.single() as Annotation.Text).color)
        vm.updateText(text.id, " ")
        assertTrue(state.edit.annotations.isEmpty())
    }

    @Test
    fun backgroundToolsAddMaskSteps() {
        open()
        vm.selectTool(EditorTool.BACKGROUND)
        vm.autoRemoveBackground()
        vm.removeRegionAt(EditPoint(0.1f, 0.1f))
        vm.setBackgroundMode(BackgroundMode.RESTORE)
        vm.finishStroke(listOf(EditPoint(0.5f, 0.5f)))
        val ops = state.edit.background.ops
        assertTrue(ops[0] is MaskOp.Auto && ops[1] is MaskOp.Seed)
        assertTrue((ops[2] as MaskOp.Brush).restore)
        assertTrue(state.edit.hasTransparency)
        vm.resetBackground()
        assertTrue(state.edit.background.isIdentity)
    }

    @Test
    fun saveUsesTheSettingOrAsks() {
        open(SaveBehavior.ASK)
        vm.setAdjust(AdjustKind.WARMTH, 0.5f)
        vm.requestSave()
        stores.drain()
        assertTrue(state.askingBehavior)
        vm.confirmAsk(SaveBehavior.COPY, remember = false)
        stores.drain()
        assertEquals(SaveBehavior.COPY, saver.saved?.second)
        assertEquals(0.5f, saver.saved?.first?.adjust?.warmth)
    }
}
