// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {
    @Test
    fun `select and play-pause toggle, but not while held`() {
        assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, PlayerKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, PlayerKeys.actionFor(KeyEvent.KEYCODE_ENTER, 0))
        assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, PlayerKeys.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        assertNull(PlayerKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER, 1))
    }

    @Test
    fun `left and right seek, and repeat while held`() {
        assertEquals(PlayerAction.SEEK_BACK, PlayerKeys.actionFor(KeyEvent.KEYCODE_DPAD_LEFT, 0))
        assertEquals(PlayerAction.SEEK_FORWARD, PlayerKeys.actionFor(KeyEvent.KEYCODE_DPAD_RIGHT, 3))
        assertEquals(PlayerAction.SEEK_BACK, PlayerKeys.actionFor(KeyEvent.KEYCODE_MEDIA_REWIND, 0))
        assertEquals(PlayerAction.SEEK_FORWARD, PlayerKeys.actionFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0))
    }

    @Test
    fun `back and menu are left to navigation`() {
        assertNull(PlayerKeys.actionFor(KeyEvent.KEYCODE_BACK, 0))
        assertFalse(PlayerKeys.consumes(KeyEvent.KEYCODE_BACK))
        assertFalse(PlayerKeys.consumes(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `up and down are consumed so focus stays on the video`() {
        assertNull(PlayerKeys.actionFor(KeyEvent.KEYCODE_DPAD_UP, 0))
        assertTrue(PlayerKeys.consumes(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(PlayerKeys.consumes(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `times format like a TV player`() {
        assertEquals("0:00", PlayerKeys.formatTime(-5))
        assertEquals("1:05", PlayerKeys.formatTime(65_400))
        assertEquals("10:34", PlayerKeys.formatTime(634_709))
        assertEquals("1:02:03", PlayerKeys.formatTime(3_723_000))
    }
}
