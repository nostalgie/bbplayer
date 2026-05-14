/**
 * Tests for [LockTaskManager] — verifies kiosk-mode logic including
 * device-owner / admin-active checks, lock-task start/stop,
 * and device-admin intent creation.
 *
 * Uses Mockito to mock DevicePolicyManager and Activity.
 * Uses Robolectric so that android.util.Log does not throw.
 */
package com.dima.kidsvideoplayer.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockTaskManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDpm: DevicePolicyManager
    private lateinit var manager: LockTaskManager

    @Before
    fun setUp() {
        mockContext = mock()
        mockDpm = mock()
        whenever(mockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
            .thenReturn(mockDpm)
        whenever(mockContext.packageName).thenReturn("com.dima.kidsvideoplayer")
        manager = LockTaskManager(mockContext)
    }

    // --- isDeviceOwner() ---

    @Test
    fun isDeviceOwner_returnsTrueWhenAppIsDeviceOwner() {
        whenever(mockDpm.isDeviceOwnerApp("com.dima.kidsvideoplayer")).thenReturn(true)

        assertThat(manager.isDeviceOwner()).isTrue()
    }

    @Test
    fun isDeviceOwner_returnsFalseWhenAppIsNotDeviceOwner() {
        whenever(mockDpm.isDeviceOwnerApp("com.dima.kidsvideoplayer")).thenReturn(false)

        assertThat(manager.isDeviceOwner()).isFalse()
    }

    // --- isAdminActive() ---

    @Test
    fun isAdminActive_returnsTrueWhenAdminIsActive() {
        whenever(mockDpm.isAdminActive(any())).thenReturn(true)

        assertThat(manager.isAdminActive()).isTrue()
    }

    @Test
    fun isAdminActive_returnsFalseWhenAdminIsNotActive() {
        whenever(mockDpm.isAdminActive(any())).thenReturn(false)

        assertThat(manager.isAdminActive()).isFalse()
    }

    // --- startKioskMode() ---

    @Test
    fun startKioskMode_asDeviceOwner_whitelistsPackagesAndStartsLockTask() {
        val mockActivity = mock<Activity>()
        whenever(mockDpm.isDeviceOwnerApp("com.dima.kidsvideoplayer")).thenReturn(true)

        manager.startKioskMode(mockActivity)

        verify(mockDpm).setLockTaskPackages(any(), any())
        verify(mockActivity).startLockTask()
    }

    @Test
    fun startKioskMode_notDeviceOwner_doesNotWhitelistButStillStartsLockTask() {
        val mockActivity = mock<Activity>()
        whenever(mockDpm.isDeviceOwnerApp("com.dima.kidsvideoplayer")).thenReturn(false)

        manager.startKioskMode(mockActivity)

        verify(mockDpm, never()).setLockTaskPackages(any(), any())
        verify(mockActivity).startLockTask()
    }

    @Test
    fun startKioskMode_handlesExceptionGracefully() {
        val mockActivity = mock<Activity>()
        whenever(mockDpm.isDeviceOwnerApp("com.dima.kidsvideoplayer")).thenReturn(true)
        whenever(mockActivity.startLockTask()).thenThrow(SecurityException("test"))

        // Should not throw — exception is caught internally
        manager.startKioskMode(mockActivity)
    }

    // --- stopKioskMode() ---

    @Test
    fun stopKioskMode_callsStopLockTaskOnActivity() {
        val mockActivity = mock<Activity>()

        manager.stopKioskMode(mockActivity)

        verify(mockActivity).stopLockTask()
    }

    @Test
    fun stopKioskMode_handlesExceptionGracefully() {
        val mockActivity = mock<Activity>()
        whenever(mockActivity.stopLockTask()).thenThrow(IllegalStateException("not locked"))

        // Should not throw — exception is caught internally
        manager.stopKioskMode(mockActivity)
    }

    // --- getDeviceAdminIntent() ---

    @Test
    fun getDeviceAdminIntent_containsCorrectAction() {
        val intent = manager.getDeviceAdminIntent()

        assertThat(intent.action).isEqualTo(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
    }

    @Test
    fun getDeviceAdminIntent_containsDeviceAdminExtra() {
        val intent = manager.getDeviceAdminIntent()

        assertThat(intent.hasExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN)).isTrue()
    }

    @Test
    fun getDeviceAdminIntent_containsExplanationExtra() {
        val intent = manager.getDeviceAdminIntent()

        val explanation = intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION)
        assertThat(explanation).isNotNull()
        assertThat(explanation).isNotEmpty()
    }

    @Test
    fun getDeviceAdminIntent_adminComponentHasCorrectPackageName() {
        val intent = manager.getDeviceAdminIntent()

        val admin = intent.getParcelableExtra<ComponentName>(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN
        )
        assertThat(admin).isNotNull()
        assertThat(admin!!.packageName).isEqualTo("com.dima.kidsvideoplayer")
    }

    @Test
    fun getDeviceAdminIntent_adminComponentHasCorrectClassName() {
        val intent = manager.getDeviceAdminIntent()

        val admin = intent.getParcelableExtra<ComponentName>(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN
        )
        assertThat(admin).isNotNull()
        assertThat(admin!!.className).isEqualTo(MyDeviceAdminReceiver::class.java.name)
    }
}
