package dev.rootiest.status

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

// The phone's "App battery usage" choice for this app, as far as the system tells apps.
enum class BatteryState { Restricted, Optimized, Unrestricted }

fun batteryState(context: Context): BatteryState = when {
    // Restricted: no background work at all, so no checks and no alerts
    context.getSystemService(ActivityManager::class.java).isBackgroundRestricted -> BatteryState.Restricted
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) -> BatteryState.Unrestricted
    else -> BatteryState.Optimized // the default: checks may wait while the phone is idle (Doze)
}

// Optimized: the system's "let the app always run in the background?" dialog. Otherwise the app's info page, where
// App battery usage is the only way out of Restricted, and back from Unrestricted.
// BatteryLife is a Play Store policy check; the app is not on Play, and alerting on outages needs timely checks.
@SuppressLint("BatteryLife")
fun batteryIntent(context: Context, state: BatteryState): Intent {
    val pkg = "package:${context.packageName}".toUri()
    return if (state == BatteryState.Optimized) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
}
