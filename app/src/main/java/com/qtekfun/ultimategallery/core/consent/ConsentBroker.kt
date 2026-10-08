package com.qtekfun.ultimategallery.core.consent

import android.content.IntentSender
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges code that needs a system consent dialog (MediaStore write, trash and delete requests) and
 * the activity that can show it. Callers [request] and suspend; `MainActivity` collects [requests],
 * launches each [IntentSender] and reports the outcome through [onResult]. One dialog at a time.
 */
@Singleton
class ConsentBroker @Inject constructor() {
    class Request(val intentSender: IntentSender, internal val result: CompletableDeferred<Boolean>)

    private val channel = Channel<Request>(Channel.UNLIMITED)
    private val mutex = Mutex()

    @Volatile
    private var current: CompletableDeferred<Boolean>? = null

    /** Requests the activity should show, in order. */
    val requests: Flow<Request> = channel.receiveAsFlow()

    /** Shows the system dialog behind [intentSender]; returns true when the user allowed it. */
    suspend fun request(intentSender: IntentSender): Boolean = mutex.withLock {
        val result = CompletableDeferred<Boolean>()
        current = result
        channel.send(Request(intentSender, result))
        try {
            result.await()
        } finally {
            current = null
        }
    }

    /** Called by the activity when the dialog closes. */
    fun onResult(allowed: Boolean) {
        current?.complete(allowed)
    }
}
