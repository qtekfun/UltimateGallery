package com.qtekfun.ultimategallery.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The photos chosen for watermarking, in the order they were picked. It lives outside the screens
 * so the picker can span several folders and hand the result to the editor.
 */
@Singleton
class BatchSelection @Inject constructor() {
    private val _ids = MutableStateFlow<List<Long>>(emptyList())
    val ids: StateFlow<List<Long>> = _ids.asStateFlow()

    fun set(ids: List<Long>) {
        _ids.value = ids.distinct()
    }

    fun clear() {
        _ids.value = emptyList()
    }
}
