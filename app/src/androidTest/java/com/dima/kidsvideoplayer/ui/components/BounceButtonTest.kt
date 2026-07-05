package com.dima.kidsvideoplayer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BounceButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bounceButton_displaysText() {
        composeTestRule.setContent {
            BounceButton(
                text = "Play",
                onClick = {},
                backgroundColor = androidx.compose.ui.graphics.Color.Green
            )
        }
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
    }
}
