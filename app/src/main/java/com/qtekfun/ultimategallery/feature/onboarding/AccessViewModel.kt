package com.qtekfun.ultimategallery.feature.onboarding

import androidx.lifecycle.ViewModel
import com.qtekfun.ultimategallery.data.media.MediaAccess
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class AccessViewModel @Inject constructor(private val access: MediaAccess) : ViewModel() {
    private val _level = MutableStateFlow(access.level())
    val level: StateFlow<MediaAccessLevel> = _level.asStateFlow()

    val permissions: Array<String> get() = access.permissionsToRequest

    /** Re-reads the granted permissions; call after returning from a dialog or the system settings. */
    fun refresh() {
        _level.value = access.level()
    }
}
