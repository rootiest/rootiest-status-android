package dev.rootiest.status.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.ui.theme.palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appearance: Appearance, onAppearance: (Appearance) -> Unit, onBack: () -> Unit) {
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
            item { SectionHeader("Appearance") }
            item { AppearanceChoice(appearance, onAppearance) }
        }
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
