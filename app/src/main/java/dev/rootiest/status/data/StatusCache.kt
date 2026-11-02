package dev.rootiest.status.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

// The last good snapshot, so the widget still has something to show offline.
class StatusCache(context: Context) {
    private val file = File(context.filesDir, "last-status.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(view: StatusView) {
        file.writeText(json.encodeToString(view))
    }

    fun load(): StatusView? = runCatching { json.decodeFromString<StatusView>(file.readText()) }.getOrNull()
}
