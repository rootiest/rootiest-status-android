package dev.rootiest.status.data

// Settings > About > Report a problem: a new ticket on the support desk (rootiest-hub), filled in with the category, the status page as the
// service, and what the app runs on. It opens the app's new-ticket form; the person still reviews it and sends it.
fun reportProblemTicket(app: String, android: String, phone: String) = NewTicket(
    Category = "problem",
    Service = "status",
    Subject = "Android app: ",
    Body = "**App:** $app\n**Android:** $android\n**Phone:** $phone\n\nWhat happened, and what did you expect?\n",
)
