// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.jsengine

import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.backrow.core.model.NoOpLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator: QuickJS is native code. Covers what :core:innertube's JVM tests
 * fake: that the engine runs, keeps globals, survives deep recursion and loads the bundled EJS
 * solver. Solving against a real player is checked by playing a video (DEVICE-QUIRKS.md) and by
 * the nightly canary.
 */
@RunWith(AndroidJUnit4::class)
class QuickJsRuntimeTest {
    private val runtime = QuickJsRuntime(NoOpLogger)

    @Test
    fun evaluatesAndKeepsGlobalsBetweenCalls() =
        runTest {
            assertEquals("3", runtime.evaluate("var a = 1 + 2; a"))
            assertEquals("hello 3", runtime.evaluate("'hello ' + a"))
        }

    @Test
    fun resetStartsAnEmptyContext() =
        runTest {
            runtime.evaluate("globalThis.kept = 'yes'; 0")
            runtime.reset()
            assertEquals("undefined", runtime.evaluate("typeof kept"))
        }

    @Test
    fun deepRecursionIsAJsErrorNotACrash() =
        runTest {
            try {
                runtime.evaluate("function f(n) { return f(n + 1) + 1; } f(0)")
                fail("expected a stack overflow error")
            } catch (e: Exception) {
                assertTrue(e.toString(), e.toString().contains("stack", ignoreCase = true))
            }
            assertEquals("ok", runtime.evaluate("'ok'"))
        }

    @Test
    fun loadsTheBundledEjsSolver() =
        runTest {
            val lib = resource("yt.solver.lib.min.js")
            val core = resource("yt.solver.core.min.js")
            assertEquals("function", runtime.evaluate("$lib\nObject.assign(globalThis, lib);\n$core\ntypeof jsc"))
            assertEquals("object", runtime.evaluate("typeof meriyah"))
        }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/dk/backrow/core/innertube/ejs/$name")) { "missing $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
}
