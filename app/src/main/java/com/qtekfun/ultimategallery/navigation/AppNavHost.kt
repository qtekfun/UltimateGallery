package com.qtekfun.ultimategallery.navigation

import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qtekfun.ultimategallery.core.ui.LocalNavAnimatedScope
import com.qtekfun.ultimategallery.core.ui.LocalSharedTransitionScope
import com.qtekfun.ultimategallery.data.media.MediaAccessLevel
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.feature.export.ExportProgressScreen
import com.qtekfun.ultimategallery.feature.gallery.FolderScreen
import com.qtekfun.ultimategallery.feature.gallery.HomeScreen
import com.qtekfun.ultimategallery.feature.onboarding.AccessActions
import com.qtekfun.ultimategallery.feature.viewer.ViewerScreen
import com.qtekfun.ultimategallery.feature.watermark.WatermarkEditorScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(accessLevel: MediaAccessLevel, accessActions: AccessActions, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val context = LocalContext.current
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = nav, startDestination = Routes.HOME, modifier = modifier) {
                destination(Routes.HOME) {
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
                destination(Routes.PICKER) {
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
                destination(
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
                        onOpenMedia = { item, _ -> nav.navigate(Routes.viewer(item.bucketId, item.id)) },
                        onWatermark = { nav.navigate(Routes.WATERMARK) },
                        onShare = { items -> share(context, items) }
                    )
                }
                destination(
                    Routes.VIEWER,
                    arguments = listOf(
                        navArgument("bucketId") { type = NavType.LongType },
                        navArgument("mediaId") { type = NavType.LongType }
                    )
                ) {
                    ViewerScreen(
                        onBack = { nav.popBackStack() },
                        onWatermark = { nav.navigate(Routes.WATERMARK) },
                        onShare = { item -> share(context, listOf(item)) }
                    )
                }
                destination(Routes.WATERMARK) {
                    WatermarkEditorScreen(
                        onBack = { nav.popBackStack() },
                        onExportStarted = { jobId -> nav.navigate(Routes.export(jobId)) }
                    )
                }
                destination(Routes.EXPORT, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) {
                    ExportProgressScreen(
                        onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                        onOpenFolder = { folder ->
                            nav.popBackStack(Routes.HOME, inclusive = false)
                            nav.navigate(Routes.folder(folder.bucketId, folder.name))
                        }
                    )
                }
                destination(Routes.SETTINGS) { WatermarkPlaceholder(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

/** A destination whose content can use the shared transition and animated scopes of the nav host. */
private fun NavGraphBuilder.destination(route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable () -> Unit) {
    composable(route, arguments = arguments) {
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content() }
    }
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
