package io.github.pierorivera1.dinogym.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.pierorivera1.dinogym.di.SettingsStore
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local settings, kept in the `settings` DataStore file. Not in Room: the theme is a
 * choice for this phone and is never synced.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @SettingsStore private val store: DataStore<Preferences>,
) {
    /** The chosen theme. SYSTEM until one is chosen, or when the file cannot be read. */
    val themeMode: Flow<ThemeMode> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ThemeModeCodec.decode(it[THEME_KEY]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_KEY] = ThemeModeCodec.encode(mode) }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}

/**
 * How a [ThemeMode] is stored: its name. Anything unknown (a value from a later version, a
 * damaged file) reads as SYSTEM, so the app always opens with a theme.
 */
object ThemeModeCodec {
    fun encode(mode: ThemeMode): String = mode.name

    fun decode(stored: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
}
