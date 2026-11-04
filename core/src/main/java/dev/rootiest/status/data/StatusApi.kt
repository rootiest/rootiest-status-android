package dev.rootiest.status.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class StatusApi(
    private val credentials: () -> Credentials? = { null },
    private val baseUrl: String = "https://status.rootiest.dev",
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): StatusView = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/status").apply {
            credentials()?.let {
                header("Authorization", "Bearer ${it.token}")
                header("X-Rootiest-Proof", it.proof)
            }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parse(response.body.string())
        }
    }

    // Gives the hub this phone's push address (rootiest-hub POST /app/push), which it wakes after each alert.
    suspend fun registerPush(address: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/app/push").post(address.toRequestBody("text/plain".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("push address refused: HTTP ${response.code}")
        }
    }

    fun parse(body: String): StatusView = json.decodeFromString(body)
}
