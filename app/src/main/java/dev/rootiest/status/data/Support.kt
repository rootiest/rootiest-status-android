package dev.rootiest.status.data

import java.net.URLEncoder

// Settings > About > Report a problem: a new ticket on the support desk (rootiest-hub), filled in by its prefill link
// with the category, the status page as the service, and what the app runs on. The person still reviews it and sends it.
fun supportTicketUrl(app: String, android: String, phone: String): String {
    val body = "**App:** $app\n**Android:** $android\n**Phone:** $phone\n\nWhat happened, and what did you expect?\n"
    val query = listOf("category" to "problem", "service" to "status", "subject" to "Android app: ", "body" to body)
        .joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    return "https://support.rootiest.dev/new?$query"
}
