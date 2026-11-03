package dev.rootiest.status.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import dev.rootiest.status.Channel
import dev.rootiest.status.Notifier
import dev.rootiest.status.PushStatus
import dev.rootiest.status.R
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.pushStatus
import dev.rootiest.status.ui.theme.palette

// What the settings screen shows of the phone's state; read again whenever the screen resumes,
// since the user may have just changed it in the phone's settings.
private data class SystemState(
    val canNotify: Boolean,
    val channelsOn: Map<Channel, Boolean>,
    val push: PushStatus,
    val lastCheck: Long?,
)

private fun readSystemState(context: Context) = SystemState(
    canNotify = Notifier.canNotify(context),
    channelsOn = Channel.entries.associateWith { Notifier.channelOn(context, it) },
    push = pushStatus(context),
    lastCheck = StatusCache(context).statesSavedAt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    mutedCount: Int,
    onOpenWatchList: () -> Unit,
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
            item { SectionHeader("Instant alerts") }
            item { PushCard(system) }
            item { SectionHeader("Appearance") }
            item { AppearanceChoice(appearance, onAppearance) }
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
                Triple("On, through ${push.distributor}", "Last push ${ago(push.lastPushAt) ?: "not yet received"}", false)
        }
        SettingRow(title, summary, warn = warn)
        Divider()
        SettingRow("Background check", "Every 15 minutes; last ${ago(system.lastCheck) ?: "not run yet"}")
    }
}

// "5 minutes ago", or null for no time
private fun ago(millis: Long?): String? =
    millis?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }

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

private val appearanceLabels = listOf(
    Appearance.System to "System default",
    Appearance.Light to "Light",
    Appearance.Dark to "Dark",
)

@Composable
private fun AppearanceChoice(selected: Appearance, onSelect: (Appearance) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup()) {
            appearanceLabels.forEach { (value, label) ->
                // the whole row is the target, so a tap on the label works too
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}
