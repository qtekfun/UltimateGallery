package com.qtekfun.ultimategallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimategallery.core.theme.UltimateGalleryTheme
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import com.qtekfun.ultimategallery.feature.onboarding.AccessGate
import com.qtekfun.ultimategallery.feature.onboarding.PartialAccessBanner
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltimateGalleryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AccessGate { level, actions ->
                        Column(modifier = Modifier.safeDrawingPadding()) {
                            if (level == MediaAccessLevel.PARTIAL) PartialAccessBanner(actions)
                            Placeholder()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.gallery_placeholder))
    }
}
