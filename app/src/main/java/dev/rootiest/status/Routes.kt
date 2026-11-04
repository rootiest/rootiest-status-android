package dev.rootiest.status

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 keys: the back stack is a list of these, saved across process death, so they are @Serializable.

@Serializable
data object Home : NavKey

// A service is identified by its group and name, as on the web page.
@Serializable
data class ServiceDetail(val group: String, val name: String) : NavKey

@Serializable
data object Settings : NavKey

// Settings > Notifications > the services and servers to notify about.
@Serializable
data object WatchList : NavKey

// The account menu > Support: the person's tickets on the support desk.
@Serializable
data object SupportTickets : NavKey

// One ticket's thread.
@Serializable
data class SupportTicket(val id: Long) : NavKey

// Opening a ticket, filled in from a prefill (Settings > About > Report a problem) or blank.
@Serializable
data class NewSupportTicket(
    val category: String = "",
    val service: String = "",
    val subject: String = "",
    val body: String = "",
) : NavKey

// Settings > About > Open-source licenses.
@Serializable
data object Licenses : NavKey
