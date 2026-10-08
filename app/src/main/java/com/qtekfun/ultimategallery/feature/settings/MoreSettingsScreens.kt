package com.qtekfun.ultimategallery.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.BuildConfig
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.ThemeMode
import com.qtekfun.ultimategallery.feature.gallery.MAX_FOLDER_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MAX_PHOTO_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MIN_FOLDER_COLUMNS
import com.qtekfun.ultimategallery.feature.gallery.MIN_PHOTO_COLUMNS
import com.qtekfun.ultimategallery.feature.watermark.ExportPanel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val SOURCE_URL = "https://github.com/qtekfun/UltimateGallery"

@Composable
private fun <T> RadioGroup(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = value == selected, onClick = {
                    onSelect(value)
                }, role = Role.RadioButton).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(stringResource(label), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)
    )
}

/** Settings → Appearance: theme, dynamic color and the default grid sizes. */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_appearance), onBack, modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.theme))
            RadioGroup(
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark),
                s.themeMode,
                viewModel::setThemeMode
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.dynamic_color_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = s.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
            }
            SectionTitle(stringResource(R.string.grid_default_title))
            ColumnsSlider(
                stringResource(R.string.grid_folder_columns),
                s.folderGridColumns,
                MIN_FOLDER_COLUMNS,
                MAX_FOLDER_COLUMNS,
                viewModel::setFolderColumns
            )
            ColumnsSlider(stringResource(R.string.grid_photo_columns), s.photoGridColumns, MIN_PHOTO_COLUMNS, MAX_PHOTO_COLUMNS, viewModel::setPhotoColumns)
            Text(
                stringResource(R.string.grid_pinch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun ColumnsSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = min.toFloat()..max.toFloat(), steps = max - min - 1)
    }
}

/** Settings → Editing: what saving an edited photo does. */
@Composable
fun EditSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_edit), onBack, modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionTitle(stringResource(R.string.save_behavior))
            RadioGroup(
                listOf(SaveBehavior.ASK to R.string.save_ask, SaveBehavior.COPY to R.string.save_copy, SaveBehavior.OVERWRITE to R.string.save_overwrite),
                s.saveBehavior,
                viewModel::setSaveBehavior
            )
            Text(
                stringResource(R.string.save_overwrite_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/** Settings → Export defaults: used for new profiles. */
@Composable
fun ExportDefaultsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_export), onBack, modifier) { padding ->
        ExportPanel(s.exportDefaults, viewModel::setExportDefaults, Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

private data class LicenseEntry(val name: String, val version: String, val license: String)

private suspend fun loadLicenses(context: Context): List<LicenseEntry> = withContext(Dispatchers.IO) {
    runCatching {
        val json = context.assets.open("licenses/artifacts.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val licenses = o.optJSONArray("spdxLicenses")
            val names = (0 until (licenses?.length() ?: 0)).joinToString(", ") { licenses!!.getJSONObject(it).optString("identifier") }
            LicenseEntry(
                "${o.getString("groupId")}:${o.getString("artifactId")}",
                o.optString("version"),
                names.ifEmpty {
                    o.optJSONArray("unknownLicenses")?.toString().orEmpty()
                }
            )
        }.sortedBy { it.name }
    }.getOrDefault(emptyList())
}

/** Settings → About: version, credits, privacy statement and the open-source licenses in use. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val licenses by produceState(emptyList<LicenseEntry>()) { value = loadLicenses(context) }
    SettingsScaffold(stringResource(R.string.settings_about), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.about_tagline), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.about_privacy), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_license), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.about_inspired),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) } }) {
                        Text(stringResource(R.string.about_source))
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.about_open_source)) }
            items(licenses, key = { it.name }) { entry ->
                Column(Modifier.fillMaxWidth().clickable(enabled = false) {}.padding(horizontal = 24.dp, vertical = 6.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        entry.version + "  ·  " + entry.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
