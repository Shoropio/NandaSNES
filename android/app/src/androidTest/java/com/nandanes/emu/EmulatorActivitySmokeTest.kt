package com.nandanes.emu

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
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

@RunWith(AndroidJUnit4::class)
class EmulatorActivityInvalidRomTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val invalidIntent = Intent(context, EmulatorActivity::class.java).apply {
        putExtra("romPath", "C:/definitely-missing/not_found.sfc")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @get:Rule
    val scenarioRule = ActivityScenarioRule<EmulatorActivity>(invalidIntent)

    @Test
    fun invalidRomPath_showsFriendlyErrorAndKeepsPickerVisible() {
        onView(withText("La ROM no existe")).check(matches(isDisplayed()))
        onView(withText("Elegir ROM")).check(matches(isDisplayed()))
    }
}
