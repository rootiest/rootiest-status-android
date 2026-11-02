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
data class TokenInfo(val name: String, val expiresAt: Long)

@Serializable
private data class Envelope(val e: Long, val v: Visitor)

@Serializable
private data class Visitor(val Name: String = "")

private val json = Json { ignoreUnknownKeys = true }

fun tokenInfo(token: String): TokenInfo? = runCatching {
    val body = String(Base64.getUrlDecoder().decode(token.substringBefore('.')))
    val env = json.decodeFromString<Envelope>(body)
    TokenInfo(env.v.Name, env.e)
}.getOrNull()

// The sign-in kept in the app's private storage. The pending proof is saved before the browser opens, so a sign-in
// still completes if Android stops the app meanwhile.
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // The address to open: starts a sign-in with a new proof.
    fun start(baseUrl: String = "https://status.rootiest.dev"): String {
        val proof = newProof()
        prefs.edit { putString("pending", proof) }
        return "$baseUrl/app/login?challenge=${challengeOf(proof)}"
    }

    // Keeps the token the hub sent back; false when no sign-in was waiting for it.
    fun complete(token: String): Boolean {
        val proof = prefs.getString("pending", null) ?: return false
        if (tokenInfo(token) == null) return false
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
