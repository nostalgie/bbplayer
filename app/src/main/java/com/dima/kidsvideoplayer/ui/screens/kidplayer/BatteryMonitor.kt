package com.dima.kidsvideoplayer.ui.screens.kidplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean
)

fun parseBatteryInfo(intent: Intent): BatteryInfo {
    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val level = if (rawLevel >= 0 && scale > 0) {
        (rawLevel * 100 / scale).coerceIn(0, 100)
    } else {
        0
    }
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(level = level, isCharging = isCharging)
}

@Composable
fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    var batteryInfo by remember { mutableStateOf(BatteryInfo(level = 0, isCharging = false)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        val stickyIntent = context.registerReceiver(null, filter)
        stickyIntent?.let { batteryInfo = parseBatteryInfo(it) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                batteryInfo = parseBatteryInfo(intent)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryInfo
}
