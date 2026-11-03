package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportTest {
    @Test
    fun ticketLinkFillsInTheForm() {
        val url = supportTicketUrl("1.5.0 (10500)", "16 (API 36)", "Google Pixel 9 Pro")
        assertEquals(
            "https://support.rootiest.dev/new?category=problem&service=status&subject=Android%20app%3A%20&body=" +
                "**App%3A**%201.5.0%20%2810500%29%0A**Android%3A**%2016%20%28API%2036%29%0A**Phone%3A**%20Google%20Pixel%209%20Pro" +
                "%0A%0AWhat%20happened%2C%20and%20what%20did%20you%20expect%3F%0A",
            url,
        )
    }
}
