package com.qtekfun.ultimategallery.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.feature.gallery.FolderScreen
import com.qtekfun.ultimategallery.feature.gallery.HomeScreen
import com.qtekfun.ultimategallery.feature.onboarding.AccessActions

@Composable
fun AppNavHost(accessLevel: MediaAccessLevel, accessActions: AccessActions, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val context = LocalContext.current
    NavHost(navController = nav, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                accessLevel = accessLevel,
                accessActions = accessActions,
                pickMode = false,
                onOpenFolder = { nav.navigate(Routes.folder(it.bucketId, it.name)) },
                onStartWatermark = { nav.navigate(Routes.PICKER) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onBack = {}
            )
        }
        composable(Routes.PICKER) {
            HomeScreen(
                accessLevel = accessLevel,
                accessActions = accessActions,
                pickMode = true,
                onOpenFolder = { nav.navigate(Routes.folder(it.bucketId, it.name, pick = true)) },
                onStartWatermark = {},
                onOpenSettings = {},
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            Routes.FOLDER,
            arguments = listOf(
                navArgument("bucketId") { type = NavType.LongType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("pick") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            FolderScreen(
                onBack = { nav.popBackStack() },
                onOpenMedia = { item, _ -> openExternally(context, item) },
                onWatermark = { nav.navigate(Routes.WATERMARK) },
                onShare = { items -> share(context, items) }
            )
        }
        composable(Routes.WATERMARK) { WatermarkPlaceholder(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { WatermarkPlaceholder(onBack = { nav.popBackStack() }) }
    }
}

private fun openExternally(context: android.content.Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}

private fun share(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, items[0].uri).setType(items[0].mimeType)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            .setType("*/*")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
