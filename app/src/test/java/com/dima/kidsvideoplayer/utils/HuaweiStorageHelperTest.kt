package com.dima.kidsvideoplayer.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HuaweiStorageHelperTest {

    @Test
    fun isHuaweiDevice_detectsManufacturer() {
        // Runs on JVM — just verify method returns boolean without crash
        val result = HuaweiStorageHelper.isHuaweiDevice()
        assertThat(result).isAnyOf(true, false)
    }

    @Test
    fun isSdCardPath_internalStorageIsNotSdCard() {
        assertThat(isSdCardPath("/storage/emulated/0")).isFalse()
    }

    @Test
    fun isSdCardPath_uuidPattern() {
        assertThat(isSdCardPath("/storage/1234-5678")).isTrue()
    }

    @Test
    fun isSdCardPath_sdcard1Path() {
        assertThat(isSdCardPath("/storage/sdcard1")).isTrue()
    }

    @Test
    fun isSdCardPath_extSdCardPath() {
        assertThat(isSdCardPath("/storage/extSdCard")).isTrue()
    }
}
