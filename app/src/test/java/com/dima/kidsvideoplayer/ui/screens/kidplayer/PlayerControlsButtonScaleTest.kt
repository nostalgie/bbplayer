package com.dima.kidsvideoplayer.ui.screens.kidplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlsButtonScaleTest {

    @Test
    fun scale_returnsOne_whenHeightIsEnough() {
        assertEquals(1f, playerControlsButtonScale(600f))
        assertEquals(1f, playerControlsButtonScale(490f))
    }

    @Test
    fun sliderStartPadding_matchesButtonColumnWidth() {
        assertEquals(102f, playerControlsSliderStartPadding(1f).value)
        assertEquals(51f, playerControlsSliderStartPadding(0.5f).value)
    }
}
