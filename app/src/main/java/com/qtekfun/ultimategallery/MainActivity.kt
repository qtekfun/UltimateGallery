package com.qtekfun.ultimategallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.core.theme.UltimateGalleryTheme
import com.qtekfun.ultimategallery.core.ui.ProvideHaptics
import com.qtekfun.ultimategallery.data.prefs.AppSettings
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.prefs.ThemeMode
import com.qtekfun.ultimategallery.feature.onboarding.AccessGate
import com.qtekfun.ultimategallery.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var consentBroker: ConsentBroker

    @Inject lateinit var settingsRepository: SettingsRepository

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        consentBroker.onResult(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                consentBroker.requests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            }
        }
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ProvideHaptics(settings.hapticsEnabled) {
                UltimateGalleryTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AccessGate { level, actions -> AppNavHost(level, actions) }
                    }
                }
            }
        }
    }
}
