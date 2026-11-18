package io.github.pierorivera1.dinogym.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One thing that ships in the APK. A font's licence text is bundled in [asset] and opens in
 * place; a library's licence is a well-known text, so its row links to [url].
 */
data class Licence(
    @StringRes val name: Int,
    @StringRes val licence: Int,
    val asset: String? = null,
    val url: String? = null,
)

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

/** The fonts in res/font. Their OFL texts are in assets/licenses. */
val FontLicences = listOf(
    Licence(R.string.lib_inter, R.string.licence_ofl, asset = "licenses/inter_OFL.txt"),
    Licence(R.string.lib_pixelify_sans, R.string.licence_ofl, asset = "licenses/pixelify_sans_OFL.txt"),
)

/**
 * The libraries in the release APK (the `implementation` lines of app/build.gradle.kts).
 * Keep this list in step when a dependency is added.
 */
val LibraryLicences = listOf(
    Licence(R.string.lib_androidx, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_compose, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_navigation3, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_room, R.string.licence_apache, url = APACHE_URL),
    // sqlite-bundled compiles SQLite itself into the APK; SQLite has no licence, it is public domain.
    Licence(R.string.lib_sqlite, R.string.licences_public_domain, url = "https://sqlite.org/copyright.html"),
    Licence(R.string.lib_datastore, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_kotlin, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_hilt, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_ktor, R.string.licence_apache, url = APACHE_URL),
    Licence(R.string.lib_credentials, R.string.licence_apache, url = APACHE_URL),
    // googleid and the Google Play services parts it brings are not open source.
    Licence(R.string.lib_googleid, R.string.licence_android_sdk, url = "https://developer.android.com/studio/terms"),
)

/**
 * The licences screen as a navigation entry. Keeps which font is open (surviving rotation)
 * and reads its licence text from the assets off the main thread.
 */
@Composable
fun LicencesEntry(onBack: () -> Unit) {
    val context = LocalContext.current
    var openAsset by rememberSaveable { mutableStateOf<String?>(null) }
    val text by produceState<String?>(null, openAsset) {
        value = null
        val asset = openAsset ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    LicencesScreen(
        openAsset = openAsset,
        openText = text,
        onBack = onBack,
        onToggle = { asset -> openAsset = if (openAsset == asset) null else asset },
        onOpenUrl = { context.openLink(it) },
    )
}

/**
 * Open-source licences (no mock; same style as Settings): FONTS, whose rows open their full
 * licence text in place, and LIBRARIES, whose rows open the licence in the browser.
 * [openAsset] is the font whose licence is open and [openText] its text (null while loading).
 */
@Composable
fun LicencesScreen(
    openAsset: String?,
    openText: String?,
    onBack: () -> Unit,
    onToggle: (asset: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(
            title = stringResource(R.string.licences_title),
            onBack = onBack,
            backDescription = stringResource(R.string.licences_back_cd),
        )
        Text(
            stringResource(R.string.licences_intro),
            style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.muted,
        )
        Field(stringResource(R.string.licences_fonts_label)) {
            ListBox {
                FontLicences.forEachIndexed { i, item ->
                    val open = item.asset == openAsset
                    LicenceRow(
                        item = item,
                        divider = i < FontLicences.lastIndex || open,
                        icon = if (open) R.drawable.ic_px_minus else R.drawable.ic_px_plus,
                        actionDescription = stringResource(if (open) R.string.licences_hide_cd else R.string.licences_show_cd),
                        onClick = { onToggle(item.asset!!) },
                    )
                    if (open && openText != null) {
                        Text(
                            openText,
                            style = DinoTheme.type.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = colors.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (i < FontLicences.lastIndex) Modifier.dottedBottomLine(colors.surface2) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        Field(stringResource(R.string.licences_libraries_label)) {
            ListBox {
                LibraryLicences.forEachIndexed { i, item ->
                    LicenceRow(
                        item = item,
                        divider = i < LibraryLicences.lastIndex,
                        icon = R.drawable.ic_px_chevron,
                        actionDescription = stringResource(R.string.licences_open_cd),
                        onClick = { onOpenUrl(item.url!!) },
                    )
                }
            }
        }
    }
}

/** The list box of Settings' ABOUT: `surface` fill with a stepped `outline` border. */
@Composable
private fun ListBox(content: @Composable () -> Unit) {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline),
    ) { content() }
}

/** A row: the project's name, its licence under it in `muted`, and [icon] on the right. */
@Composable
private fun LicenceRow(
    item: Licence,
    divider: Boolean,
    icon: Int,
    actionDescription: String,
    onClick: () -> Unit,
) {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (divider) Modifier.dottedBottomLine(colors.surface2) else Modifier)
            // The name and licence are read from the texts; the label says what a tap does.
            .clickable(role = Role.Button, onClickLabel = actionDescription, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(item.name), style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.outline)
            Text(stringResource(item.licence), style = DinoTheme.type.body.copy(fontSize = 13.sp), color = colors.muted)
        }
        PixelIcon(icon, colors.muted, size = 24.dp)
    }
}
