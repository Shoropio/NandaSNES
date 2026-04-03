package com.nandanes.emu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulatorActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EmulatorActivity>()

    @Test
    fun launch_showsRomPickerPrimaryActions() {
        composeRule.onNodeWithText("NandaNes").assertIsDisplayed()
        composeRule.onNodeWithText("Elegir ROM").assertIsDisplayed()
        composeRule.onNodeWithText("Acerca de").assertIsDisplayed()
    }
}
