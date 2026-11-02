package dev.rootiest.status.data

import android.content.Context
import androidx.core.content.edit

// Light or dark: follow the phone (the default), or always one of them.
enum class Appearance { System, Light, Dark }

// The user's settings, kept in the app's private storage.
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var appearance: Appearance
        // an unknown name (from a newer version, say) falls back to the default
        get() = Appearance.entries.find { it.name == prefs.getString("appearance", null) } ?: Appearance.System
        set(value) = prefs.edit { putString("appearance", value.name) }
}
