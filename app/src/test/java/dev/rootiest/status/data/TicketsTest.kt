package dev.rootiest.status.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The fixtures support-tickets.json and support-thread.json are what rootiest-hub's own API handlers answered (written by its
// tests): a ticket answered by staff about Gitea, and a closed one.
class TicketsTest {
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun parsesTheDesksList() {
        val list = SupportApi({ null }).parseList(resource("support-tickets.json")).Tickets
        assertEquals(listOf(2L to "closed", 1L to "answered"), list.map { it.ID to it.Status })
        assertEquals("Cannot push to Gitea", list[1].Subject)
        assertEquals("Gitea", list[1].ServiceName)
        assertEquals("", list[0].ServiceName) // not about one service
    }

    @Test
    fun parsesTheDesksThread() {
        val t = SupportApi({ null }).parseThread(resource("support-thread.json"))
        assertEquals("answered", t.Ticket.Status)
        assertEquals(listOf("Alice" to "user", "Rootiest Support" to "staff"), t.Messages.map { it.Author to it.Role })
        assertTrue(t.Messages[0].Body.contains("**Gitea**")) // Markdown as written
    }

    @Test
    fun statusesReadAsWhoseTurnItIs() {
        assertEquals("Waiting for support", statusLabel("open"))
        assertEquals("Answered", statusLabel("answered"))
        assertEquals("Closed", statusLabel("closed"))
    }

    // An OkHttp client that answers every request itself and remembers it.
    private class FakeDesk(val code: Int, val body: String) : Interceptor {
        var seen: Request? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            seen = chain.request()
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun api(desk: FakeDesk, creds: Credentials? = Credentials("tok", "proof")) =
        SupportApi({ creds }, "https://desk.test", OkHttpClient.Builder().addInterceptor(desk).build())

    @Test
    fun sendsTheTokenAndProof() = runBlocking {
        val desk = FakeDesk(200, resource("support-tickets.json"))
        assertEquals(2, api(desk).tickets().size)
        val req = desk.seen!!
        assertEquals("https://desk.test/api/tickets", req.url.toString())
        assertEquals("Bearer tok", req.header("Authorization"))
        assertEquals("proof", req.header("X-Rootiest-Proof"))
    }

    @Test
    fun aRefusedTokenMeansSignInAgain() = runBlocking {
        try {
            api(FakeDesk(401, """{"Errors":["Sign in again."]}""")).tickets()
            fail("no error")
        } catch (_: SignInNeeded) {
        }
    }

    @Test
    fun notSignedInAsksNothingOfTheDesk() = runBlocking {
        val desk = FakeDesk(200, "{}")
        try {
            api(desk, creds = null).tickets()
            fail("no error")
        } catch (_: SignInNeeded) {
        }
        assertNull(desk.seen)
    }

    @Test
    fun theDesksOwnWordsComeThrough() = runBlocking {
        try {
            api(FakeDesk(404, """{"Errors":["That ticket does not exist."]}""")).thread(9)
            fail("no error")
        } catch (e: DeskRefused) {
            assertEquals(404, e.code)
            assertEquals("That ticket does not exist.", e.message)
        }
    }
}
