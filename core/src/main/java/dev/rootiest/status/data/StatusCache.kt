package dev.rootiest.status.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

// The last good snapshot, so the widget still has something to show offline, and the states the background
// job last saw, to tell what changed.
class StatusCache(dir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(dir, "last-status.json")
    private val statesFile = File(dir, "last-states.json")
    private val pendingFile = File(dir, "pending-outages.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(view: StatusView) {
        file.replaceWith(json.encodeToString(view))
    }

    // The snapshot, or null when there is none or it is older than maxAgeMillis.
    fun load(maxAgeMillis: Long = Long.MAX_VALUE, nowMillis: Long = System.currentTimeMillis()): StatusView? {
        if (nowMillis - file.lastModified() > maxAgeMillis) return null
        return runCatching { json.decodeFromString<StatusView>(file.readText()) }.getOrNull()
    }

    fun saveStates(states: Map<String, String>) {
        statesFile.replaceWith(json.encodeToString(states))
    }

    // When the background job last saved the states (only it does), or null before its first run.
    fun statesSavedAt(): Long? = statesFile.lastModified().takeIf { it > 0 }

    fun loadStates(): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(statesFile.readText()) }.getOrDefault(emptyMap())

    // The outages waiting out Settings > Notify about an outage (see damp).
    fun savePending(pending: Map<String, Pending>) {
        pendingFile.replaceWith(json.encodeToString(pending))
    }

    fun loadPending(): Map<String, Pending> =
        runCatching { json.decodeFromString<Map<String, Pending>>(pendingFile.readText()) }.getOrDefault(emptyMap())
}

// Writes a temporary file beside this one and renames it over it, so a reader never sees half a file, even when the app
// is stopped mid-write or the widget and the background job save at once (each writes its own temporary file).
private fun File.replaceWith(text: String) {
    val tmp = File.createTempFile(name, ".tmp", parentFile)
    try {
        tmp.writeText(text)
        if (!tmp.renameTo(this)) error("could not replace $this")
    } finally {
        tmp.delete() // gone already after a successful rename
    }
}
