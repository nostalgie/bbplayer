package com.dima.kidsvideoplayer.utils

import android.content.pm.ActivityInfo
import android.view.Surface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrientationHelperTest {

    @Test
    fun displayRotationIsLandscape_detectsLandscapeRotations() {
        assertThat(OrientationHelper.displayRotationIsLandscape(Surface.ROTATION_90)).isTrue()
        assertThat(OrientationHelper.displayRotationIsLandscape(Surface.ROTATION_270)).isTrue()
        assertThat(OrientationHelper.displayRotationIsLandscape(Surface.ROTATION_0)).isFalse()
        assertThat(OrientationHelper.displayRotationIsLandscape(Surface.ROTATION_180)).isFalse()
    }

    @Test
    fun sensorAngleIsLandscape_usesAccelerometerQuadrants() {
        assertThat(OrientationHelper.sensorAngleIsLandscape(90)).isTrue()
        assertThat(OrientationHelper.sensorAngleIsLandscape(270)).isTrue()
        assertThat(OrientationHelper.sensorAngleIsLandscape(0)).isFalse()
        assertThat(OrientationHelper.sensorAngleIsLandscape(180)).isFalse()
    }

    @Test
    fun needsOrientationCorrection_whenLandscapeDeviceHasPortraitWindow() {
        assertThat(
            OrientationHelper.needsOrientationCorrection(
                Surface.ROTATION_90,
                windowWidth = 1080,
                windowHeight = 2400
            )
        ).isTrue()
    }

    @Test
    fun needsOrientationCorrection_whenOrientationsMatch() {
        assertThat(
            OrientationHelper.needsOrientationCorrection(
                Surface.ROTATION_90,
                windowWidth = 2400,
                windowHeight = 1080
            )
        ).isFalse()
        assertThat(
            OrientationHelper.needsOrientationCorrection(
                Surface.ROTATION_0,
                windowWidth = 1080,
                windowHeight = 2400
            )
        ).isFalse()
    }

    @Test
    fun requestedOrientationForDisplayRotation_mapsAllRotations() {
        assertThat(OrientationHelper.requestedOrientationForDisplayRotation(Surface.ROTATION_90))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertThat(OrientationHelper.requestedOrientationForDisplayRotation(Surface.ROTATION_0))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
}
