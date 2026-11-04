package dev.rootiest.status.ui

import androidx.navigation3.runtime.NavKey
import dev.rootiest.status.Home
import dev.rootiest.status.ServiceDetail
import dev.rootiest.status.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

// A long-press on the Quick Settings tile opens Settings, over whatever is open.
class OpenSettingsTest {
    @Test
    fun settingsOpenOverTheCurrentScreen() {
        val stack = mutableListOf<NavKey>(Home, ServiceDetail("Code", "Gitea"))
        openSettings(stack)
        assertEquals(listOf(Home, ServiceDetail("Code", "Gitea"), Settings), stack)
    }

    @Test
    fun settingsAlreadyOnScreenStayAsTheyAre() {
        val stack = mutableListOf<NavKey>(Home, Settings)
        openSettings(stack)
        assertEquals(listOf(Home, Settings), stack)
    }
}
