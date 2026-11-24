// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

/**
 * A JavaScript engine, as `:core:innertube` needs it to solve player-JS challenges (ADR-0009).
 *
 * Implemented outside this module (`:core:jsengine`, QuickJS) because a JS engine is native code
 * and this module is pure Kotlin/JVM. The implementation knows nothing about YouTube: it only
 * runs the scripts it is handed.
 */
interface JsRuntime {
    /**
     * Evaluates [script] as a classic (non-module) script in one context that persists between
     * calls, so globals defined by one call are visible to the next. Returns the value of the
     * script's last expression, converted to a string.
     *
     * Calls may be slow (seconds on a weak device); never call this on the main thread.
     */
    suspend fun evaluate(script: String): String

    /**
     * Throws away the context and everything defined in it, to give its memory back. The next
     * [evaluate] starts from an empty context.
     */
    suspend fun reset()
}
