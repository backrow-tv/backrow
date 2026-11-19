// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.model

/**
 * Logging for every module, including the pure Kotlin/JVM ones that cannot see
 * `android.util.Log`. The app wires an implementation that logs with tag `Backrow`,
 * so `adb logcat -s Backrow` shows everything.
 *
 * Never pass tokens, cookies, account data or SponsorBlock user ids to a logger.
 */
interface Logger {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String, throwable: Throwable? = null)

    fun error(message: String, throwable: Throwable? = null)
}

/** A [Logger] that discards everything. Useful as a default in tests. */
object NoOpLogger : Logger {
    override fun debug(message: String) = Unit

    override fun info(message: String) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit

    override fun error(message: String, throwable: Throwable?) = Unit
}
