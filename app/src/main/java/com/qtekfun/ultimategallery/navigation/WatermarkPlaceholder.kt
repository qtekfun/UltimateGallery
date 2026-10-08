package com.qtekfun.ultimategallery.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimategallery.R

/** Temporary destination until the real screen exists. */
@Composable
fun WatermarkPlaceholder(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Button(onClick = onBack) { Text(stringResource(R.string.watermark_soon)) }
    }
}
