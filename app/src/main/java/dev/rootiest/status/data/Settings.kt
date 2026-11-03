package dev.rootiest.status.data

import android.content.Context
import androidx.core.content.edit

// Light or dark: follow the phone (the default), or always one of them.
enum class Appearance { System, Light, Dark }

// How the list shows services: as the page does, with problems at the top of each group, or only the problems.
enum class ServiceListMode { All, ProblemsFirst, ProblemsOnly }

// How often the background job runs on its own; null minutes: never, only when a push wakes it.
enum class RefreshInterval(val minutes: Long?) { Every15(15), Every30(30), Hourly(60), PushOnly(null) }

// The user's settings, kept in the app's private storage.
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var appearance: Appearance
        // an unknown name (from a newer version, say) falls back to the default
        get() = Appearance.entries.find { it.name == prefs.getString("appearance", null) } ?: Appearance.System
        set(value) = prefs.edit { putString("appearance", value.name) }

    var refreshInterval: RefreshInterval
        get() = RefreshInterval.entries.find { it.name == prefs.getString("refresh", null) } ?: RefreshInterval.Every15
        set(value) = prefs.edit { putString("refresh", value.name) }

    var serviceList: ServiceListMode
        get() = ServiceListMode.entries.find { it.name == prefs.getString("service-list", null) } ?: ServiceListMode.All
        set(value) = prefs.edit { putString("service-list", value.name) }

    // Services and servers the user does not want notifications about, by name. Muting rather than watching, so one
    // that appears later is notified about until muted.
    var muted: Set<String>
        get() = prefs.getStringSet("muted", null)?.toSet() ?: emptySet() // a copy: the returned set must not be changed
        set(value) = prefs.edit { putStringSet("muted", value) }
}
