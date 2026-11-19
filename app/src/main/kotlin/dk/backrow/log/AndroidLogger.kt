// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.log

import android.util.Log
import dk.backrow.core.model.Logger

/** Sends every log line to logcat under one tag, so `adb logcat -s Backrow` shows the app. */
class AndroidLogger : Logger {
    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "Backrow"
    }
}
