package dev.rootiest.status.ui

import androidx.navigation3.runtime.NavKey
import dev.rootiest.status.Home
import dev.rootiest.status.Settings
import dev.rootiest.status.SupportTicket
import dev.rootiest.status.SupportTickets
import org.junit.Assert.assertEquals
import org.junit.Test

// The launcher icon's Support shortcut opens the ticket list.
class OpenSupportTest {
    @Test
    fun supportOpensOverTheStatusList() {
        val stack = mutableListOf<NavKey>(Home)
        openSupport(stack)
        assertEquals(listOf(Home, SupportTickets), stack)
    }

    @Test
    fun supportOpensOverAnotherScreen() {
        val stack = mutableListOf<NavKey>(Home, Settings)
        openSupport(stack)
        assertEquals(listOf(Home, Settings, SupportTickets), stack)
    }

    @Test
    fun aTicketOverTheListGoesBackToTheList() {
        val stack = mutableListOf<NavKey>(Home, SupportTickets, SupportTicket(7))
        openSupport(stack)
        assertEquals(listOf(Home, SupportTickets), stack)
    }

    @Test
    fun theListAlreadyOnScreenStaysAsItIs() {
        val stack = mutableListOf<NavKey>(Home, SupportTickets)
        openSupport(stack)
        assertEquals(listOf(Home, SupportTickets), stack)
    }
}
