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
// it; tampering with it just makes the hub ignore it.
data class TokenInfo(val name: String, val expiresAt: Long, val challenge: String)

@Serializable
private data class Envelope(val e: Long, val v: Visitor)

@Serializable
private data class Visitor(val Name: String = "", val Challenge: String = "")

private val json = Json { ignoreUnknownKeys = true }

fun tokenInfo(token: String): TokenInfo? = runCatching {
    val body = String(Base64.getUrlDecoder().decode(token.substringBefore('.')))
    val env = json.decodeFromString<Envelope>(body)
    TokenInfo(env.v.Name, env.e, env.v.Challenge)
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

    // The sign-in to send, or null when signed out or expired.
    fun credentials(nowSeconds: Long = System.currentTimeMillis() / 1000): Credentials? {
        val token = prefs.getString("token", null) ?: return null
        val proof = prefs.getString("proof", null) ?: return null
        val info = tokenInfo(token) ?: return null
        return if (info.expiresAt > nowSeconds) Credentials(token, proof) else null
    }

    fun signedInAs(): String? = credentials()?.let { tokenInfo(it.token)?.name }

    fun signOut() {
        prefs.edit { clear() }
    }
}
