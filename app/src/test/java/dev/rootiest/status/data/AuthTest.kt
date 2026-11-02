package dev.rootiest.status.data

import org.junit.Assert.assertEquals
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
    @Test
    fun readsNameAndExpiryFromAToken() {
        val body = """{"e":1800000000,"v":{"At":1,"Name":"Rootiest","Admin":true,"Challenge":"x"}}"""
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray()) + ".sig"
        assertEquals(TokenInfo("Rootiest", 1800000000), tokenInfo(token))
        assertNull(tokenInfo("not a token"))
    }
}
