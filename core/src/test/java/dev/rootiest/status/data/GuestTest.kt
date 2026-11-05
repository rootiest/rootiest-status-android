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

// The email path (rootiest-hub guest_api.go). The support-guest-*.json fixtures are the desk's real answers, written by its handlers in a test:
// its guest form, a refused request, a used link (with a token for pat@example.com, asked for with the challenge of 43 "p"s), and a used-up link.
class GuestTest {
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    private class FakeDesk(val code: Int, val body: String) : Interceptor {
        var seen: Request? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            seen = chain.request()
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    // signed out, as someone on the email path is
    private fun api(desk: FakeDesk) = SupportApi({ null }, "https://desk.test", OkHttpClient.Builder().addInterceptor(desk).build())

    private fun Request.text() = okio.Buffer().also { body!!.writeTo(it) }.readUtf8()

    @Test
    fun readsTheDesksLinks() {
        val desk = "support.rootiest.dev"
        assertEquals(DeskLink.Confirm("abc"), deskLink("https", desk, "/guest/confirm", "abc"))
        assertEquals(DeskLink.Open("x.y"), deskLink("https", desk, "/guest/open", "x.y"))
        assertEquals(DeskLink.GuestForm, deskLink("https", desk, "/guest", null))
        assertEquals(DeskLink.GuestForm, deskLink("https", desk, "/guest/", null))
        assertNull(deskLink("https", desk, "/guest/confirm", null)) // no token
        assertNull(deskLink("https", desk, "/guest/confirm", " "))
        assertNull(deskLink("https", desk, "/guest/move", "abc")) // moving tickets to a RootiestID needs the browser
        assertNull(deskLink("https", desk, "/t/1", null))
        assertNull(deskLink("http", desk, "/guest/confirm", "abc"))
        assertNull(deskLink("https", "status.rootiest.dev", "/", null))
        assertNull(deskLink("https", "support.rootiest.dev.evil.test", "/guest/confirm", "abc"))
        assertNull(deskLink("dev.rootiest.status", null, "/support-signed-in", null))
    }

    @Test
    fun theGuestFormHasTheDesksTwoTopicsAndNoServices() = runBlocking {
        val desk = FakeDesk(200, resource("support-guest-form.json"))
        val form = api(desk).guestForm()
        assertEquals(listOf("account", "general"), form.Categories.map { it.ID })
        assertTrue(form.Services.isEmpty())
        assertEquals("https://desk.test/api/guest", desk.seen!!.url.toString())
        assertNull(desk.seen!!.header("Authorization"))
    }

    @Test
    fun aGuestRequestSendsEveryFieldWithoutASignIn() = runBlocking {
        val desk = FakeDesk(202, """{"Email":"pat@example.com"}""")
        api(desk).requestGuestTicket(GuestTicket("pat@example.com", "Pat", "account", Subject = "s", Body = "b"))
        val req = desk.seen!!
        assertEquals("POST", req.method)
        assertEquals("https://desk.test/api/guest", req.url.toString())
        assertNull(req.header("Authorization"))
        assertEquals("""{"Email":"pat@example.com","Name":"Pat","Category":"account","Importance":"normal","Subject":"s","Body":"b"}""", req.text())

        api(desk).requestGuestSignIn("pat@example.com")
        assertEquals("https://desk.test/api/guest/signin", desk.seen!!.url.toString())
        assertEquals("""{"Email":"pat@example.com"}""", desk.seen!!.text())
    }

    @Test
    fun aRefusedRequestCarriesTheDesksReasons() = runBlocking {
        try {
            api(FakeDesk(422, resource("support-guest-422.json"))).requestGuestTicket(GuestTicket())
            fail("no error")
        } catch (e: DeskRefused) {
            assertEquals("Enter one email address, like name@example.com.", e.messages.first())
            assertEquals(4, e.messages.size)
        }
    }

    @Test
    fun usingALinkSendsItsTokenAndAChallenge() = runBlocking {
        val proof = "p".repeat(43)
        val desk = FakeDesk(200, resource("support-guest-signed-in.json"))
        val answer = api(desk).useLink(DeskLink.Confirm("abc"), challengeOf(proof))
        assertEquals("https://desk.test/api/guest/confirm", desk.seen!!.url.toString())
        assertEquals("""{"Token":"abc","Challenge":"${challengeOf(proof)}"}""", desk.seen!!.text())
        assertEquals(1L, answer.Ticket)
        // the desk's token names the address, says it is a guest, and answers this proof
        val info = tokenInfo(answer.Token)!!
        assertEquals(TokenInfo("Pat", info.expiresAt, challengeOf(proof), "pat@example.com", guest = true), info)
        assertTrue(tokenMatches(answer.Token, proof))

        api(desk).useLink(DeskLink.Open("x.y"), challengeOf(proof))
        assertEquals("https://desk.test/api/guest/open", desk.seen!!.url.toString())
    }

    // a used-up link is the desk's 404, in its words, not "sign in again": there is no sign-in to drop
    @Test
    fun aUsedLinkSaysSo() = runBlocking {
        try {
            api(FakeDesk(404, resource("support-guest-404.json"))).useLink(DeskLink.Confirm("abc"), challengeOf("p".repeat(43)))
            fail("no error")
        } catch (e: DeskRefused) {
            assertEquals(listOf("That link has expired or was already used."), e.messages)
        }
    }
}
