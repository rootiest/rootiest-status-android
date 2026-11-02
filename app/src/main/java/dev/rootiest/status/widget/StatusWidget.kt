package dev.rootiest.status.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.rootiest.status.MainActivity
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.problems
import dev.rootiest.status.ui.theme.Mocha
import dev.rootiest.status.ui.theme.toneOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class StatusWidget : GlanceAppWidget() {
    private val small = DpSize(110.dp, 40.dp)
    private val large = DpSize(180.dp, 110.dp)

    // Two layouts: the headline alone, or the headline with what is wrong and when it was fetched.
    override val sizeMode = SizeMode.Responsive(setOf(small, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = StatusCache(context)
        // Fetch on every update; fall back to the last good snapshot and say it is stale.
        val fresh = runCatching { StatusApi(AuthStore(context)::credentials).fetch() }.getOrNull()
        fresh?.let(cache::save)
        val view = fresh ?: cache.load()
        provideContent { Content(view, stale = fresh == null) }
    }

    @Composable
    private fun Content(view: StatusView?, stale: Boolean) {
        val showDetail = LocalSize.current.height >= large.height
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(Mocha.Mantle).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(12.dp).cornerRadius(6.dp).background(toneOf(view?.Level ?: "none"))) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(view?.Headline ?: "Status unavailable", style = text(Mocha.Text, 15, bold = true), maxLines = 2)
            }
            if (view != null && showDetail) {
                Spacer(GlanceModifier.height(6.dp))
                val problems = view.problems()
                problems.take(MAX_ROWS).forEach { Text(it, style = text(Mocha.Red, 13), maxLines = 1) }
                if (problems.size > MAX_ROWS) Text("and ${problems.size - MAX_ROWS} more", style = text(Mocha.Subtext0, 12))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    (if (stale) "Offline · as of " else "Updated ") + localTime(view.Now),
                    style = text(if (stale) Mocha.Yellow else Mocha.Overlay0, 11),
                )
            }
        }
    }

    private fun text(color: Color, size: Int, bold: Boolean = false) =
        TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

    private companion object {
        const val MAX_ROWS = 4
        val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        fun localTime(at: String): String =
            runCatching { timeFormat.format(Instant.parse(at).atZone(ZoneId.systemDefault())) }.getOrDefault(at)
    }
}
