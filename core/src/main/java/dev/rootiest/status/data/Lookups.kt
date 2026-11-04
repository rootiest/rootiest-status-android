package dev.rootiest.status.data

// Plain functions over a snapshot, kept out of the UI so they can be unit tested.

fun StatusView.service(group: String, name: String): ServiceView? =
    Groups.firstOrNull { it.Name == group }?.Services?.firstOrNull { it.Name == name }

// The snapshot's events about one service, newest first (the API already sends them newest first).
fun StatusView.eventsFor(service: String): List<Event> =
    Events.orEmpty().filter { it.Kind == "service" && it.Subject == service }

// "2026-11-01: 100.00% up" -> "100.00% up"; a day without data -> "no data".
fun BarCell.summary(): String = Title.removePrefix("$Day: ")
