package com.dima.kidsvideoplayer.ui.screens.kidplayer

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatteryMonitorTest {

    @Test
    fun parseBatteryInfo_returnsPercentLevel() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 85)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }

        val info = parseBatteryInfo(intent)

        assertEquals(85, info.level)
        assertFalse(info.isCharging)
    }

    @Test
    fun parseBatteryInfo_detectsCharging() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 60)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }

        val info = parseBatteryInfo(intent)

        assertEquals(60, info.level)
        assertTrue(info.isCharging)
    }

    @Test
    fun parseBatteryInfo_detectsFullAsCharging() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
        }

        val info = parseBatteryInfo(intent)

        assertEquals(100, info.level)
        assertTrue(info.isCharging)
    }

    @Test
    fun parseBatteryInfo_handlesLowLevel() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 15)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }

        val info = parseBatteryInfo(intent)

        assertEquals(15, info.level)
        assertFalse(info.isCharging)
    }

    @Test
    fun parseBatteryInfo_scalesLevelToPercent() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 42)
            putExtra(BatteryManager.EXTRA_SCALE, 200)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }

        val info = parseBatteryInfo(intent)

        assertEquals(21, info.level)
    }
}
