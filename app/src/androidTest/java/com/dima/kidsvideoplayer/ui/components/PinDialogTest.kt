package com.dima.kidsvideoplayer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pinDialog_displaysTitle() {
        composeTestRule.setContent {
            PinDialog(onDismiss = {}, onPinCorrect = {}, title = "Test PIN")
        }
        composeTestRule.onNodeWithText("Test PIN").assertIsDisplayed()
    }

    @Test
    fun pinDialog_cancelButtonIsDisplayed() {
        composeTestRule.setContent {
            PinDialog(onDismiss = {}, onPinCorrect = {})
        }
        composeTestRule.onNodeWithText("Отмена").assertIsDisplayed()
    }
}
