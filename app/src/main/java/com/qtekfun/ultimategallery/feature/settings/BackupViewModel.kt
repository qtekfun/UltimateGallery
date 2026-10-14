package com.qtekfun.ultimategallery.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.BuildConfig
import com.qtekfun.ultimategallery.data.backup.BackupCodec
import com.qtekfun.ultimategallery.data.backup.SettingsBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The result of an export or import, for the screen to turn into a message. */
sealed interface BackupEvent {
    data class Exported(val profilesWithoutImage: Int) : BackupEvent

    data class Imported(val profilesAdded: Int) : BackupEvent

    /** The picked file is not a settings backup. */
    data object Invalid : BackupEvent

    /** The file could not be read or written. */
    data object Failed : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(@ApplicationContext private val context: Context, private val backup: SettingsBackup) : ViewModel() {
    private val _pending = MutableStateFlow<BackupCodec.Contents?>(null)

    /** A parsed backup waiting for the user to confirm the import. */
    val pending: StateFlow<BackupCodec.Contents?> = _pending.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    /** Writes the backup to [target], a document the user picked. */
    fun export(target: Uri) {
        viewModelScope.launch {
            val event = try {
                val result = backup.export(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("No output stream")
                    out.use { it.write(result.json.toByteArray(Charsets.UTF_8)) }
                }
                BackupEvent.Exported(result.profilesWithoutImage)
            } catch (_: IOException) {
                BackupEvent.Failed
            } catch (_: SecurityException) {
                BackupEvent.Failed
            }
            _events.send(event)
        }
    }

    /** Reads the backup at [source]; on success it waits in [pending] for confirmation. */
    fun load(source: Uri) {
        viewModelScope.launch {
            val event = try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(source)?.use { it.readAtMost(BackupCodec.MAX_BYTES) } }
                val contents = bytes?.let { BackupCodec.decode(String(it, Charsets.UTF_8)) }
                if (contents == null) {
                    BackupEvent.Invalid
                } else {
                    _pending.value = contents
                    null
                }
            } catch (_: IOException) {
                BackupEvent.Failed
            } catch (_: SecurityException) {
                BackupEvent.Failed
            }
            if (event != null) _events.send(event)
        }
    }

    fun confirmImport() {
        val contents = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch { _events.send(BackupEvent.Imported(backup.restore(contents).profilesAdded)) }
    }

    fun cancelImport() {
        _pending.value = null
    }

    /** Reads the whole stream, or returns null as soon as it exceeds [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
