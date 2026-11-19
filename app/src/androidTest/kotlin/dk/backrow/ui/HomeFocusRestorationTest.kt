// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.backrow.MainActivity
import dk.backrow.ui.home.HomeTestTags
import dk.backrow.ui.home.PlaceholderCard
import dk.backrow.ui.settings.SettingsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Remote-only navigation (CLAUDE.md rule 8): initial focus, Settings reachable by D-pad and
 * by Menu, and focus restored to the same element after Back. Runs on the Android TV
 * emulator: `./gradlew :app:connectedDebugAndroidTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeFocusRestorationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun card(row: Int, index: Int) = compose.onNodeWithTag(HomeTestTags.card(PlaceholderCard(row, index)))

    @Test
    fun firstCardHasFocusOnLaunch() {
        compose.waitForIdle()
        card(0, 0).assertIsFocused()
    }

    @Test
    fun menuOpensSettingsAndBackRestoresTheCard() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        card(1, 2).assertIsFocused()

        press(Key.Menu)
        compose.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()

        back()
        card(1, 2).assertIsFocused()
    }

    @Test
    fun gearOpensSettingsWithDpadAndBackRestoresTheGear() {
        press(Key.DirectionUp)
        compose.onNodeWithTag(HomeTestTags.SETTINGS_GEAR).assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()

        back()
        compose.onNodeWithTag(HomeTestTags.SETTINGS_GEAR).assertIsFocused()
    }

    @Test
    fun rowRemembersItsCardWhenFocusComesBack() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        card(0, 3).assertIsFocused()
    }
}
