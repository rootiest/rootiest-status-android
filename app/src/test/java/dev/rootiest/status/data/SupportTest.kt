package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportTest {
    @Test
    fun reportAProblemFillsInTheForm() {
        val t = reportProblemTicket("1.5.0 (10500)", "16 (API 36)", "Google Pixel 9 Pro")
        assertEquals(NewTicket(Category = "problem", Service = "android", Importance = "normal", Subject = "Android app: ",
            Body = "**App:** 1.5.0 (10500)\n**Android:** 16 (API 36)\n**Phone:** Google Pixel 9 Pro\n\nWhat happened, and what did you expect?\n",
            NotifyEmail = true), t)
    }
}
