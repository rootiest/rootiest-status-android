package dev.rootiest.status.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.rootiest.status.R
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.data.SettingsStore
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.WidgetScope
import dev.rootiest.status.ui.Choice
import dev.rootiest.status.ui.ChoiceCard
import dev.rootiest.status.ui.Label
import dev.rootiest.status.ui.Note
import dev.rootiest.status.ui.theme.RootiestTheme
import kotlinx.coroutines.launch

// What one widget shows. The home screen opens this when the widget is added (Android 10 and 11 only: from 12 the widget is
// added showing everything) and from its widget settings later. A choice is saved and drawn at once.
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // leaving without a choice keeps a new widget off the home screen (before Android 12) and an old one as it was
        setResult(RESULT_CANCELED, result)
        val store = WidgetStore(this)
        val view = StatusCache(this).load()
        val appearance = SettingsStore(this).appearance
        setContent {
            val dark = when (appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            RootiestTheme(dark) {
                WidgetConfigScreen(view, store.scope(widgetId), onBack = ::finish) { scope ->
                    store.setScope(widgetId, scope)
                    lifecycleScope.launch {
                        val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                        StatusWidget().update(this@WidgetConfigActivity, manager.getGlanceIdBy(widgetId))
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(view: StatusView?, selected: WidgetScope, onBack: () -> Unit, onPick: (WidgetScope) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What this widget shows") },
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceCard(
                        listOf(Choice<WidgetScope>(WidgetScope.All, "Everything", "The status page's headline and what is wrong")),
                        selected,
                        onPick,
                    )
                    Label("One section")
                    if (view == null) {
                        Note("Open the app once, so the widget knows the groups.")
                    } else {
                        // the servers only for viewers who may see them; their names come from the last snapshot
                        val sections = view.Hosts?.let { listOf(Choice<WidgetScope>(WidgetScope.Servers, "Servers", count(it.size, "server"))) }.orEmpty() +
                            view.Groups.map { Choice<WidgetScope>(WidgetScope.Group(it.Name), it.Name, count(it.Services.size, "service")) }
                        ChoiceCard(sections, selected, onPick) {
                            Note("Every server or service in it, with problems first.")
                        }
                    }
                }
            }
        }
    }
}

private fun count(n: Int, what: String) = if (n == 1) "1 $what" else "$n ${what}s"
