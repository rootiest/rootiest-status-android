package dev.rootiest.status.ui

import androidx.navigation3.runtime.NavKey
import dev.rootiest.status.Home
import dev.rootiest.status.Settings
import dev.rootiest.status.SupportTicket
import dev.rootiest.status.SupportTickets
import org.junit.Assert.assertEquals
import org.junit.Test

// Tapping a support reply's notification opens that ticket, over Support's list.
class OpenTicketTest {
    @Test
    fun fromAnywhereTheTicketOpensOverTheList() {
        val stack = mutableListOf<NavKey>(Home, Settings)
        openTicket(stack, 5)
        assertEquals(listOf(Home, Settings, SupportTickets, SupportTicket(5)), stack)
    }

    @Test
    fun fromTheListItIsNotAddedTwice() {
        val stack = mutableListOf<NavKey>(Home, SupportTickets, SupportTicket(3))
        openTicket(stack, 5)
        assertEquals(listOf(Home, SupportTickets, SupportTicket(3), SupportTicket(5)), stack)
    }

    @Test
    fun theTicketAlreadyOnScreenStaysAsItIs() {
        val stack = mutableListOf<NavKey>(Home, SupportTickets, SupportTicket(5))
        openTicket(stack, 5)
        assertEquals(listOf(Home, SupportTickets, SupportTicket(5)), stack)
    }
}
