// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.player

import android.view.KeyEvent

/** What a remote key does on the player screen. */
enum class PlayerAction { TOGGLE_PLAY_PAUSE, PLAY, PAUSE, SEEK_BACK, SEEK_FORWARD }

/**
 * Remote keys on the player (CLAUDE.md rule 8: D-pad, Select and Back are enough; media keys are
 * shortcuts). Back is not handled here: it leaves the player through the navigation back stack.
 *
 * Select toggles play/pause only on its first key-down, so holding it does not flicker;
 * Left/Right repeat while held, seeking 10 s per repeat.
 */
object PlayerKeys {
    fun actionFor(keyCode: Int, repeatCount: Int): PlayerAction? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> PlayerAction.TOGGLE_PLAY_PAUSE.takeIf { repeatCount == 0 }
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerAction.PLAY.takeIf { repeatCount == 0 }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerAction.PAUSE.takeIf { repeatCount == 0 }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerAction.SEEK_BACK
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerAction.SEEK_FORWARD
            else -> null
        }

    /** `1:05`, `12:34` or `1:02:03`. */
    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Keys the player consumes even when they do nothing now, so focus never leaves the video. */
    fun consumes(keyCode: Int): Boolean =
        keyCode in
            setOf(
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            )
}
