package dev.rootiest.status.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.net.toUri
import dev.rootiest.status.BatteryState
import dev.rootiest.status.BuildConfig
import dev.rootiest.status.Channel
import dev.rootiest.status.Notifier
import dev.rootiest.status.PushStatus
import dev.rootiest.status.R
import dev.rootiest.status.batteryIntent
import dev.rootiest.status.batteryState
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.data.NotifyAfter
import dev.rootiest.status.data.RefreshInterval
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.data.TimeStyle
import dev.rootiest.status.data.ClockHours
import dev.rootiest.status.data.DateStyle
import dev.rootiest.status.data.STRFTIME_FIELDS
import java.time.Instant
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.supportTicketUrl
import dev.rootiest.status.pushStatus
import dev.rootiest.status.ui.theme.palette

// What the settings screen shows of the phone's state; read again whenever the screen resumes,
// since the user may have just changed it in the phone's settings.
private data class SystemState(
    val canNotify: Boolean,
    val channelsOn: Map<Channel, Boolean>,
    val push: PushStatus,
    val lastCheck: Long?,
    val battery: BatteryState,
)

private fun readSystemState(context: Context) = SystemState(
    canNotify = Notifier.canNotify(context),
    channelsOn = Channel.entries.associateWith { Notifier.channelOn(context, it) },
    push = pushStatus(context),
    lastCheck = StatusCache(context).statesSavedAt(),
    battery = batteryState(context),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    serviceList: ServiceListMode,
    onServiceList: (ServiceListMode) -> Unit,
    timeFormat: TimeFormat,
    onTimeFormat: (TimeFormat) -> Unit,
    mutedCount: Int,
    notifyAfter: NotifyAfter,
    onNotifyAfter: (NotifyAfter) -> Unit,
    onOpenWatchList: () -> Unit,
    refreshInterval: RefreshInterval,
    onRefreshInterval: (RefreshInterval) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var system by remember { mutableStateOf(readSystemState(context)) }
    LifecycleResumeEffect(Unit) {
        system = readSystemState(context)
        onPauseOrDispose {}
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { SectionHeader("Notifications") }
            item { NotificationsCard(system, context, mutedCount, onOpenWatchList) }
            if (system.canNotify) {
                item {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("Notify about an outage")
                        ChoiceCard(notifyAfterChoices, notifyAfter, onNotifyAfter) {
                            Note("One that is over sooner is never notified, nor is its recovery.")
                        }
                    }
                }
            }
            item { SectionHeader("Instant alerts") }
            item { PushCard(system) }
            item { SectionHeader("Background check") }
            item { BackgroundCheckCard(system, context, refreshInterval, onRefreshInterval) }
            item { SectionHeader("Appearance") }
            item { ChoiceCard(appearanceLabels.map { (value, label) -> Choice(value, label) }, appearance, onAppearance) }
            item { SectionHeader("Service list") }
            item { ChoiceCard(serviceListChoices, serviceList, onServiceList) }
            item { SectionHeader("Times") }
            item { TimesCards(timeFormat, onTimeFormat) }
            item { SectionHeader("About") }
            item { AboutCard(context, onOpenLicenses) }
        }
    }
}

@Composable
private fun NotificationsCard(system: SystemState, context: Context, mutedCount: Int, onOpenWatchList: () -> Unit) {
    SettingsCard {
        if (!system.canNotify) {
            // Android 13+ grants the permission from this page too, so it works even after "Don't allow" twice
            SettingRow("Notifications are off", "Tap to turn them on in the phone's settings", warn = true) {
                context.startActivity(Notifier.settingsIntent(context))
            }
            return@SettingsCard
        }
        SettingRow("Outages", channelSummary(system, Channel.Outages, "A service or server went down")) {
            context.startActivity(Notifier.settingsIntent(context, Channel.Outages))
        }
        Divider()
        SettingRow("Recoveries", channelSummary(system, Channel.Recoveries, "It came back up")) {
            context.startActivity(Notifier.settingsIntent(context, Channel.Recoveries))
        }
        Divider()
        SettingRow("Services to notify about", if (mutedCount == 0) "All" else "All but $mutedCount muted", onClick = onOpenWatchList)
        Divider()
        SettingRow("Send a test notification", "Shows how an outage alert looks and sounds") { Notifier.test(context) }
    }
}

private fun channelSummary(system: SystemState, channel: Channel, on: String) =
    if (system.channelsOn[channel] == true) on else "Off"

@Composable
private fun PushCard(system: SystemState) {
    SettingsCard {
        val (title, summary, warn) = when (val push = system.push) {
            PushStatus.NoDistributor ->
                Triple("Off", "Install the ntfy app to be told within seconds instead of minutes", false)
            is PushStatus.NotRegistered ->
                Triple("Not connected", "${push.distributor} is installed but gave no address. Its default server must be ntfy.rootiest.dev", true)
            is PushStatus.On ->
                Triple("On, through ${push.distributor}", "Last push ${moment(push.lastPushAt) ?: "not yet received"}", false)
        }
        SettingRow(title, summary, warn = warn)
    }
}

// How often the app checks on its own, and when it last did. Push only is flagged while push is not on: nothing would wake it.
@Composable
private fun BackgroundCheckCard(system: SystemState, context: Context, selected: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    val pushOn = system.push is PushStatus.On
    val choices = listOf(
        Choice(RefreshInterval.Every15, "Every 15 minutes"),
        Choice(RefreshInterval.Every30, "Every 30 minutes"),
        Choice(RefreshInterval.Hourly, "Every hour"),
        Choice(
            RefreshInterval.PushOnly,
            "Only when a push arrives",
            if (pushOn) "Least battery; the widget updates only after an alert"
            else "Instant alerts are not on, so the app would never check",
            warn = !pushOn,
        ),
    )
    ChoiceCard(choices, selected, onSelect) {
        Divider()
        SettingRow("Last check", moment(system.lastCheck) ?: "Not run yet")
        Divider()
        val (title, summary) = when (system.battery) {
            BatteryState.Restricted ->
                "Battery use is restricted" to "Android stops background checks and alerts. Tap, then App battery usage: Optimized or Unrestricted"
            BatteryState.Optimized ->
                "Battery: optimized" to "Android may delay checks while the phone is idle. Tap to let the app run unrestricted"
            BatteryState.Unrestricted ->
                "Battery: unrestricted" to "Checks run on time. Tap to change it in the app's info"
        }
        SettingRow(title, summary, warn = system.battery == BatteryState.Restricted) {
            context.startActivity(batteryIntent(context, system.battery))
        }
    }
}

@Composable
private fun AboutCard(context: Context, onOpenLicenses: () -> Unit) {
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    SettingsCard {
        SettingRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Divider()
        SettingRow("Status page", STATUS_PAGE.removePrefix("https://")) { open(STATUS_PAGE) }
        Divider()
        SettingRow("Source code", "GPL-3.0-or-later, on ${SOURCE.removePrefix("https://").substringBefore('/')}") { open(SOURCE) }
        Divider()
        SettingRow("Report a problem", "Opens a ticket on the support desk, with the app and phone filled in") {
            open(
                supportTicketUrl(
                    app = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    phone = "${Build.MANUFACTURER} ${Build.MODEL}",
                ),
            )
        }
        Divider()
        SettingRow("Open-source licenses", "The libraries the app is built with", onClick = onOpenLicenses)
    }
}

private const val STATUS_PAGE = "https://status.rootiest.dev"
private const val SOURCE = "https://git.rootiest.dev/rootiest/rootiest-status-android"

// A time on the phone, as the Times setting shows it, or null for none.
@Composable
private fun moment(millis: Long?): String? = millis?.let { LocalTimeFormat.current.format(Instant.ofEpochMilli(it), Instant.now()) }

// Relative or clock; the clock's format, hours and zone. Each example is the current time less 3 hours in that choice.
@Composable
private fun TimesCards(selected: TimeFormat, onSelect: (TimeFormat) -> Unit) {
    val now = Instant.now()
    val sample = now.minusSeconds(3 * 3600)
    fun clockExample(f: TimeFormat) = f.copy(style = TimeStyle.Clock).format(sample, now)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceCard(
            listOf(
                Choice(TimeStyle.Relative, "Relative", selected.copy(style = TimeStyle.Relative).format(sample, now)),
                Choice(TimeStyle.Clock, "Clock time", clockExample(selected)),
            ),
            selected.style,
            { onSelect(selected.copy(style = it)) },
        ) {
            if (selected.style == TimeStyle.Relative) {
                Note("The choices below still apply to the widget, which always shows a clock time (a relative one would go stale between redraws), and to times over a week old.")
            }
        }
        Label("Format")
        ChoiceCard(
            listOf(
                Choice(DateStyle.Default, "Default", clockExample(selected.copy(date = DateStyle.Default))),
                Choice(DateStyle.Iso, "ISO", clockExample(selected.copy(date = DateStyle.Iso))),
                Choice(DateStyle.Custom, "Custom", clockExample(selected.copy(date = DateStyle.Custom))),
            ),
            selected.date,
            { onSelect(selected.copy(date = it)) },
        ) {
            if (selected.date == DateStyle.Custom) CustomPattern(selected, onSelect)
        }
        Label("Hours")
        ChoiceCard(
            listOf(
                Choice(ClockHours.Phone, "Phone setting", if (selected.phone24) "24-hour now" else "12-hour now"),
                Choice(ClockHours.H12, "12-hour"),
                Choice(ClockHours.H24, "24-hour"),
            ),
            selected.hours,
            { onSelect(selected.copy(hours = it)) },
        ) {
            if (selected.date == DateStyle.Custom) Note("A custom format sets its own hours: %H for 24-hour, %I or %l with %p for 12-hour.")
        }
        Label("Time zone")
        ChoiceCard(
            listOf(
                Choice(false, "Local time", "The phone's time zone"),
                Choice(true, "UTC", "As the server keeps it; daily uptime is in UTC days either way"),
            ),
            selected.utc,
            { onSelect(selected.copy(utc = it)) },
        ) {
            if (selected.date == DateStyle.Custom) Note("A custom format adds no zone name; use %Z for one.")
        }
    }
}

// The pattern, as strftime writes it, with what it gives and the fields it knows.
@Composable
private fun CustomPattern(selected: TimeFormat, onSelect: (TimeFormat) -> Unit) {
    // the field keeps its own text, so the cursor stays put while the setting saves on every change
    var text by remember { mutableStateOf(selected.pattern) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onSelect(selected.copy(pattern = it)) },
            label = { Text("Pattern") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            STRFTIME_FIELDS.joinToString("   ") { (field, gives) -> "$field $gives" },
            style = MaterialTheme.typography.bodySmall,
            color = palette.Subtext0,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = palette.Subtext0, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = palette.Surface0)
}

// A title over a smaller summary; tappable when it has an onClick.
@Composable
private fun SettingRow(title: String, summary: String, warn: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = if (warn) palette.Yellow else palette.Text)
        Spacer(Modifier.height(2.dp))
        Text(summary, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
    }
}

private val notifyAfterChoices = listOf(
    Choice(NotifyAfter.AtOnce, "At once", "Every blip, even one over in seconds"),
    Choice(NotifyAfter.Two, "After 2 minutes"),
    Choice(NotifyAfter.Five, "After 5 minutes", "As the status page's own alerts, which wait 4 minutes"),
    Choice(NotifyAfter.Fifteen, "After 15 minutes"),
)

private val serviceListChoices = listOf(
    Choice(ServiceListMode.All, "As on the status page"),
    Choice(ServiceListMode.ProblemsFirst, "Problems first", "Down services and unhealthy servers at the top of their section"),
    Choice(ServiceListMode.ProblemsOnly, "Problems only", "Hide what is up and healthy"),
)

private val appearanceLabels = listOf(
    Appearance.System to "System default",
    Appearance.Light to "Light",
    Appearance.Dark to "Dark",
)

private class Choice<T>(val value: T, val label: String, val summary: String? = null, val warn: Boolean = false)

// Radio buttons in a card, with optional rows after them.
@Composable
private fun <T> ChoiceCard(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, footer: @Composable () -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    // the whole row is the target, so a tap on the label works too
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = choice.value == selected, role = Role.RadioButton, onClick = { onSelect(choice.value) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(choice.label)
                            choice.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = if (choice.warn) palette.Yellow else palette.Subtext0)
                            }
                        }
                    }
                }
            }
            footer()
        }
    }
}
