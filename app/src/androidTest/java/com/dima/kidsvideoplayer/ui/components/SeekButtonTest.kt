package com.dima.kidsvideoplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeekButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun seekButton_displaysIcon() {
        composeTestRule.setContent {
            SeekButton(
                icon = Icons.Default.FastForward,
                contentDescription = "Seek forward",
                onSeek = {}
            )
        }
        composeTestRule.onNodeWithContentDescription("Seek forward").assertIsDisplayed()
    }
}
