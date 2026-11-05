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

// The email path, for people without a RootiestID (rootiest-hub guest.go, guest_api.go): the guest form sends this, and nothing reaches the
// queue until the link the desk emails is used. A guest's ticket is about no service, and replies always go by email too.
@Serializable
data class GuestTicket(
    val Email: String = "",
    val Name: String = "",
    val Category: String = "",
    val Importance: String = "normal",
    val Subject: String = "",
    val Body: String = "",
)

@Serializable
private data class EmailBody(val Email: String)

@Serializable
private data class LinkBody(val Token: String, val Challenge: String)

// What the desk answers for a used link: a sign-in for the email address, and the ticket to show (0: the list).
@Serializable
data class GuestSignIn(val Token: String, val Ticket: Long = 0)

// A link from one of the desk's emails, which Android opens in the app (App Links, in the manifest): "confirm" for a new ticket or a
// sign-in link (/guest/confirm?t=), "open" for the link in a reply email (/guest/open?t=), or the guest form itself (/guest).
sealed interface DeskLink {
    data class Confirm(val token: String) : DeskLink
    data class Open(val token: String) : DeskLink
    data object GuestForm : DeskLink
}

// The desk link in an https address, or null for any other address (or one with no token).
fun deskLink(scheme: String?, host: String?, path: String?, token: String?): DeskLink? {
    if (scheme != "https" || host != java.net.URI(AuthStore.SUPPORT_URL).host) return null
    return when (path?.trimEnd('/')) {
        "/guest" -> DeskLink.GuestForm
        "/guest/confirm" -> token?.takeIf { it.isNotBlank() }?.let(DeskLink::Confirm)
        "/guest/open" -> token?.takeIf { it.isNotBlank() }?.let(DeskLink::Open)
        else -> null
    }
}

// The desk's limits, the same as its web form's.
const val MAX_SUBJECT = 150

@Serializable
private data class ReplyBody(val Body: String)

@Serializable
private data class StatusBody(val Status: String)

@Serializable
private data class PushBody(val Address: String)

// What the desk's push says: "ticket <id>" when staff replied to or closed that ticket (rootiest-hub, "Telling the app about replies").
// Anything else (the status page's "refresh") is not about a ticket.
fun ticketPush(content: String): Long? = Regex("""^ticket (\d{1,18})$""").find(content.trim())?.groupValues?.get(1)?.toLongOrNull()

// The notification for a ticket the desk pushed about: from its newest staff reply, or that it was closed. Null when neither is so (a
// push can arrive after the person already read it, or for a ticket reopened since); then nothing is shown.
data class TicketNotice(val title: String, val text: String)

fun ticketNotice(thread: TicketThread): TicketNotice? {
    val last = thread.Messages.lastOrNull()
    val subject = thread.Ticket.Subject
    return when {
        last?.Role == "staff" -> TicketNotice("Support replied: $subject", firstLine(last.Body))
        thread.Ticket.Status == "closed" -> TicketNotice("Ticket closed: $subject", "Rootiest Support closed ticket #${thread.Ticket.ID}")
        else -> null
    }
}

// A message's first line with its Markdown marks taken off, for a notification's one line.
internal fun firstLine(body: String): String =
    body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1") // [text](url) -> text
        .replace(Regex("""[*_`#>]+"""), "")
        .removePrefix("- ").trim()

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

    // Tells the desk (or stops telling it) where to push about this person's tickets: the phone's UnifiedPush address.
    suspend fun registerPush(address: String) = send("/api/push", json.encodeToString(PushBody(address)), "POST") {}

    suspend fun unregisterPush(address: String) = send("/api/push", json.encodeToString(PushBody(address)), "DELETE") {}

    fun parseGuestSignIn(body: String): GuestSignIn = json.decodeFromString(body)

    // The email path: these need no sign-in. A 404 means the desk has it switched off (no mail set up).
    suspend fun guestForm(): TicketForm = send("/api/guest", null, "GET", signedIn = false) { json.decodeFromString(it) }

    // Asks the desk to email the link that sends this ticket. The answer is the same whether or not an email went out.
    suspend fun requestGuestTicket(ticket: GuestTicket) = send("/api/guest", json.encodeToString(ticket), "POST", signedIn = false) {}

    // Asks for a sign-in link for an address that has sent tickets; the same answer whether it has any.
    suspend fun requestGuestSignIn(email: String) = send("/api/guest/signin", json.encodeToString(EmailBody(email)), "POST", signedIn = false) {}

    // Uses a link from an email (DeskLink), with the challenge of a new proof: the desk answers with a sign-in bound to it.
    suspend fun useLink(link: DeskLink, challenge: String): GuestSignIn {
        val (path, token) = when (link) {
            is DeskLink.Confirm -> "/api/guest/confirm" to link.token
            is DeskLink.Open -> "/api/guest/open" to link.token
            DeskLink.GuestForm -> throw IllegalArgumentException("the guest form is not a link to use")
        }
        return send(path, json.encodeToString(LinkBody(token, challenge)), "POST", signedIn = false) { json.decodeFromString(it) }
    }

    private suspend fun <T> get(path: String, parse: (String) -> T): T = send(path, null, "GET", parse = parse)

    private suspend fun <T> post(path: String, body: String, parse: (String) -> T): T = send(path, body, "POST", parse = parse)

    private suspend fun <T> send(path: String, body: String?, method: String, signedIn: Boolean = true, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val c = if (signedIn) credentials() ?: throw SignInNeeded() else null
        val request = Request.Builder().url(baseUrl + path)
            .apply { if (c != null) header("Authorization", "Bearer ${c.token}").header("X-Rootiest-Proof", c.proof) }
            .apply { if (body != null) method(method, body.toRequestBody("application/json".toMediaType())) }
            .build()
        client.newCall(request).execute().use { response -> parse(body(response, signedIn)) }
    }

    // The body of a successful answer; otherwise the desk's error, as SignInNeeded or DeskRefused.
    private fun body(response: Response, signedIn: Boolean = true): String {
        val text = response.body.string()
        if (response.isSuccessful) return text
        if (response.code == 401 && signedIn) throw SignInNeeded()
        val messages = runCatching { json.decodeFromString<ApiErrors>(text).Errors }.getOrDefault(emptyList())
        throw DeskRefused(response.code, messages)
    }

    fun parseList(body: String): TicketList = json.decodeFromString(body)

    fun parseThread(body: String): TicketThread = json.decodeFromString(body)
}
