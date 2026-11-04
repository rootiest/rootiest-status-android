package dev.rootiest.status.data

// What the Quick Settings tile shows: the same level and headline as an Everything widget. The tile is active (lit) only when
// everything is up, so a problem shows as a dimmed tile with its headline under the name.
data class TileContent(val level: String, val subtitle: String) {
    val active: Boolean get() = level == "ok"
}

fun StatusView.tileContent(): TileContent = widgetContent(WidgetScope.All).let { TileContent(it.level, it.headline) }
