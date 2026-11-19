// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LoggerTest {
    private class RecordingLogger : Logger {
        val lines = mutableListOf<String>()

        override fun debug(message: String) {
            lines += "D $message"
        }

        override fun info(message: String) {
            lines += "I $message"
        }

        override fun warn(message: String, throwable: Throwable?) {
            lines += "W $message"
        }

        override fun error(message: String, throwable: Throwable?) {
            lines += "E $message"
        }
    }

    @Test
    fun `throwable is optional for warn and error`() {
        val logger = RecordingLogger()

        logger.warn("slow start")
        logger.error("failed", IllegalStateException("boom"))

        assertEquals(listOf("W slow start", "E failed"), logger.lines)
    }

    @Test
    fun `no-op logger accepts every level`() {
        NoOpLogger.debug("d")
        NoOpLogger.info("i")
        NoOpLogger.warn("w", RuntimeException())
        NoOpLogger.error("e")
    }
}
