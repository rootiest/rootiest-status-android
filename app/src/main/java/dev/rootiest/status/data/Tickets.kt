package dev.rootiest.status.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

// The support desk's JSON API for the Android app (rootiest-hub internal/support/api.go, docs/README.md "Support desk"). It shows the
// signed-in person their own tickets only, and never staff-only notes. Field names match Go's, as for /api/status; times are RFC 3339.

@Serializable
data class Ticket(
    val ID: Long,
    val Subject: String,
    val Status: String, // open (waiting for staff), answered (waiting for you), closed
    val Category: String = "",
    val CategoryLabel: String = "",
    val Service: String = "",
    val ServiceName: String = "", // empty when the ticket is not about one service
    val Importance: String = "",
    val ImportanceLabel: String = "",
    val NotifyEmail: Boolean = false,
    val Created: String,
    val Updated: String,
)

@Serializable
data class TicketMessage(
    val ID: Long,
    val Role: String, // user or staff
    val Author: String, // the person's name, or "Rootiest Support" for staff
    val Body: String, // Markdown, as written
    val Created: String,
)

@Serializable
data class TicketList(val Tickets: List<Ticket> = emptyList())

@Serializable
data class TicketThread(val Ticket: Ticket, val Messages: List<TicketMessage> = emptyList())

@Serializable
private data class ApiErrors(val Errors: List<String> = emptyList())

// The new-ticket form's choices for this person (GET /api/form): only the services they may see, as on the web form.
@Serializable
data class FormOption(val ID: String, val Label: String)

@Serializable
data class FormService(val ID: String, val Name: String, val Group: String = "")

@Serializable
data class TicketForm(
    val Categories: List<FormOption> = emptyList(),
    val Importances: List<FormOption> = emptyList(),
    val Services: List<FormService> = emptyList(),
    val MaxBody: Int = 5000,
)

// A new ticket, as the web form sends it. Service is empty when it is not about one service.
@Serializable
data class NewTicket(
    val Category: String = "",
    val Service: String = "",
    val Importance: String = "normal",
    val Subject: String = "",
    val Body: String = "",
    val NotifyEmail: Boolean = true,
)

// The desk's limits, the same as its web form's.
const val MAX_SUBJECT = 150

@Serializable
private data class ReplyBody(val Body: String)

@Serializable
private data class StatusBody(val Status: String)

// The desk refused the token (expired, signed out everywhere, or another sign-in's): sign in again.
class SignInNeeded : IOException("Sign in to the support desk again.")

// What the desk said was wrong, in its own words (the web form's): shown as it is.
class DeskRefused(val code: Int, val messages: List<String>) : IOException(messages.joinToString(" ").ifEmpty { "HTTP $code" })

// What people see for each status: the desk's words for whose turn it is.
fun statusLabel(status: String): String = when (status) {
    "open" -> "Waiting for support"
    "answered" -> "Answered"
    "closed" -> "Closed"
    else -> status
}

class SupportApi(
    private val credentials: () -> Credentials?,
    private val baseUrl: String = AuthStore.SUPPORT_URL,
    private val client: OkHttpClient = OkHttpClient(),
) {
    // encodeDefaults: a field left at its default (Importance "normal", NotifyEmail true) must still be sent, or Go reads it as empty or false
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun tickets(): List<Ticket> = get("/api/tickets") { parseList(it).Tickets }

    suspend fun thread(id: Long): TicketThread = get("/api/tickets/$id") { parseThread(it) }

    suspend fun form(): TicketForm = get("/api/form") { json.decodeFromString(it) }

    // Each answers with the ticket's thread as it now is.
    suspend fun create(ticket: NewTicket): TicketThread = post("/api/tickets", json.encodeToString(ticket)) { parseThread(it) }

    suspend fun reply(id: Long, body: String): TicketThread = post("/api/tickets/$id/reply", json.encodeToString(ReplyBody(body))) { parseThread(it) }

    suspend fun close(id: Long): TicketThread = post("/api/tickets/$id/status", json.encodeToString(StatusBody("closed"))) { parseThread(it) }

    private suspend fun <T> get(path: String, parse: (String) -> T): T = send(path, null, parse)

    private suspend fun <T> post(path: String, body: String, parse: (String) -> T): T = send(path, body, parse)

    private suspend fun <T> send(path: String, body: String?, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val c = credentials() ?: throw SignInNeeded()
        val request = Request.Builder().url(baseUrl + path)
            .header("Authorization", "Bearer ${c.token}")
            .header("X-Rootiest-Proof", c.proof)
            .apply { if (body != null) post(body.toRequestBody("application/json".toMediaType())) }
            .build()
        client.newCall(request).execute().use { response -> parse(body(response)) }
    }

    // The body of a successful answer; otherwise the desk's error, as SignInNeeded or DeskRefused.
    private fun body(response: Response): String {
        val text = response.body.string()
        if (response.isSuccessful) return text
        if (response.code == 401) throw SignInNeeded()
        val messages = runCatching { json.decodeFromString<ApiErrors>(text).Errors }.getOrDefault(emptyList())
        throw DeskRefused(response.code, messages)
    }

    fun parseList(body: String): TicketList = json.decodeFromString(body)

    fun parseThread(body: String): TicketThread = json.decodeFromString(body)
}
