package dev.rootiest.status.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

// The last good snapshot, so the widget still has something to show offline, and the states the background
// job last saw, to tell what changed.
class StatusCache(context: Context) {
    private val file = File(context.filesDir, "last-status.json")
    private val statesFile = File(context.filesDir, "last-states.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(view: StatusView) {
        file.writeText(json.encodeToString(view))
    }

    fun load(): StatusView? = runCatching { json.decodeFromString<StatusView>(file.readText()) }.getOrNull()

    fun saveStates(states: Map<String, String>) {
        statesFile.writeText(json.encodeToString(states))
    }

    fun loadStates(): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(statesFile.readText()) }.getOrDefault(emptyMap())
}
