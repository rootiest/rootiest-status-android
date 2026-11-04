package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthTest {
    // The same vector as rootiest-hub's challengeOf: SHA-256 of 43 "p"s, base64url without padding.
    @Test
    fun challengeMatchesTheHub() {
        assertEquals("lu5JzTzx1yIf6O9E1Dz2hEOQE5ZzlWKecRyEPPmGdSw", challengeOf("p".repeat(43)))
    }

    @Test
    fun proofsAre43RandomCharacters() {
        val a = newProof()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertTrue(a != newProof())
    }

    // A token shaped like the hub's session.Seal: base64url({"e":exp,"v":{...}}) + "." + signature.
    private fun token(challenge: String): String {
        val body = """{"e":1800000000,"v":{"At":1,"Name":"Rootiest","Admin":true,"Challenge":"$challenge"}}"""
        return Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray()) + ".sig"
    }

    @Test
    fun readsNameExpiryAndChallengeFromAToken() {
        assertEquals(TokenInfo("Rootiest", 1800000000, "x"), tokenInfo(token("x")))
        assertNull(tokenInfo("not a token"))
    }

    @Test
    fun onlyTheTokenForThisSignInMatches() {
        val proof = newProof()
        assertTrue(tokenMatches(token(challengeOf(proof)), proof))
        assertFalse(tokenMatches(token(challengeOf(newProof())), proof)) // another sign-in's token
        assertFalse(tokenMatches(token(""), proof)) // a token without a challenge
        assertFalse(tokenMatches("not a token", proof))
    }
}
