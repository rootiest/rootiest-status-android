package dev.rootiest.status.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// Signing in, as rootiest-hub's /app/login expects (its docs/README.md, "The Android app"): the app keeps a random proof,
// sends the proof's SHA-256 as the challenge, gets back a token bound to it, and sends both with every request.

data class Credentials(val token: String, val proof: String)

private val base64Url = Base64.getUrlEncoder().withoutPadding()

// 32 random bytes: 43 base64url characters.
fun newProof(): String = base64Url.encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

fun challengeOf(proof: String): String =
    base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(proof.toByteArray()))

// What the app reads from a token: who it names and when it runs out. The hub signs the token, so the app only reads
// it; tampering with it just makes the hub ignore it. On the support desk, guest is a sign-in from a link mailed to email
// (no RootiestID); the status page's tokens carry neither.
data class TokenInfo(val name: String, val expiresAt: Long, val challenge: String, val email: String = "", val guest: Boolean = false)

@Serializable
private data class Envelope(val e: Long, val v: Visitor)

@Serializable
private data class Visitor(val Name: String = "", val Challenge: String = "", val Email: String = "", val Guest: Boolean = false)

private val json = Json { ignoreUnknownKeys = true }

fun tokenInfo(token: String): TokenInfo? = runCatching {
    val body = String(Base64.getUrlDecoder().decode(token.substringBefore('.')))
    val env = json.decodeFromString<Envelope>(body)
    TokenInfo(env.v.Name, env.e, env.v.Challenge, env.v.Email, env.v.Guest)
}.getOrNull()

// Whether a token answers the sign-in started with this proof. Any app can open dev.rootiest.status:/signed-in, so a
// token for some other sign-in must not replace this one.
fun tokenMatches(token: String, proof: String): Boolean = tokenInfo(token)?.challenge == challengeOf(proof)

// The sign-in kept in the app's private storage. The pending proof is saved before the browser opens, so a sign-in
// still completes if Android stops the app meanwhile. The status page and the support desk each have their own (forStatus,
// forSupport): they are separate RootiestID clients with separate keys, so neither's token works on the other.
class AuthStore(context: Context, prefsName: String = "auth", private val baseUrl: String = STATUS_URL) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        const val STATUS_URL = "https://status.rootiest.dev"
        const val SUPPORT_URL = "https://support.rootiest.dev"

        fun forStatus(context: Context) = AuthStore(context)

        // rootiest-hub's support desk: /app/login there returns to dev.rootiest.status:/support-signed-in
        fun forSupport(context: Context) = AuthStore(context, "support-auth", SUPPORT_URL)
    }

    // The address to open: starts a sign-in with a new proof.
    fun start(): String {
        val proof = newProof()
        prefs.edit { putString("pending", proof) }
        return "$baseUrl/app/login?challenge=${challengeOf(proof)}"
    }

    // Keeps the token the hub sent back; false when no sign-in was waiting for it or it answers another one (the
    // pending sign-in then stays open for the real token).
    fun complete(token: String): Boolean {
        val proof = prefs.getString("pending", null) ?: return false
        if (!tokenMatches(token, proof)) return false
        prefs.edit { putString("token", token).putString("proof", proof).remove("pending") }
        return true
    }

    // Keeps a token the desk gave for a link from an email (rootiest-hub's /api/guest/confirm and /api/guest/open), asked for with this
    // proof's challenge. It replaces whatever sign-in was kept, as opening the link in a browser would.
    fun adopt(token: String, proof: String): Boolean {
        if (!tokenMatches(token, proof)) return false
        prefs.edit { putString("token", token).putString("proof", proof).remove("pending") }
        return true
    }

    // The sign-in to send, or null when signed out or expired.
    fun credentials(nowSeconds: Long = System.currentTimeMillis() / 1000): Credentials? {
        val token = prefs.getString("token", null) ?: return null
        val proof = prefs.getString("proof", null) ?: return null
        val info = tokenInfo(token) ?: return null
        return if (info.expiresAt > nowSeconds) Credentials(token, proof) else null
    }

    fun signedInAs(): String? = whoAmI()?.name

    // Who the kept sign-in names, or null when signed out or expired.
    fun whoAmI(): TokenInfo? = credentials()?.let { tokenInfo(it.token) }

    fun signOut() {
        prefs.edit { clear() }
    }
}
