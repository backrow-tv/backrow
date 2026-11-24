// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.jsengine

import com.dokar.quickjs.QuickJs
import dk.backrow.core.innertube.JsRuntime
import dk.backrow.core.model.Logger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * [JsRuntime] backed by QuickJS through quickjs-kt (ADR-0009).
 *
 * One QuickJS context, created on first use and kept until [reset], so globals survive between
 * calls. QuickJS is single-threaded, so every call runs on one dedicated thread. That thread has
 * a large stack: the solver parses the multi-megabyte player JS with a recursive-descent parser,
 * and QuickJS recurses on the native stack. QuickJS's own stack limit is set below the thread's,
 * so deep recursion ends in a catchable JS error rather than a native crash.
 */
class QuickJsRuntime(
    private val logger: Logger,
    private val memoryLimitBytes: Long = DEFAULT_MEMORY_LIMIT,
) : JsRuntime {
    private val dispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(null, runnable, "backrow-js", THREAD_STACK_BYTES).apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var quickJs: QuickJs? = null

    override suspend fun evaluate(script: String): String =
        mutex.withLock {
            withContext(dispatcher) {
                val js = quickJs ?: create().also { quickJs = it }
                js.evaluate<Any?>(script).toString()
            }
        }

    override suspend fun reset() {
        mutex.withLock {
            withContext(dispatcher) {
                quickJs?.let { js ->
                    logger.debug("QuickJS reset; was using ${js.memoryUsage.memoryUsedSize / 1024} KiB")
                    js.close()
                }
                quickJs = null
            }
        }
    }

    /** Bytes the current context uses, for measurements; 0 if no context exists. */
    suspend fun memoryUsedBytes(): Long =
        mutex.withLock { withContext(dispatcher) { quickJs?.memoryUsage?.memoryUsedSize ?: 0L } }

    private fun create(): QuickJs =
        QuickJs.create(dispatcher).apply {
            memoryLimit = memoryLimitBytes
            maxStackSize = QUICKJS_STACK_BYTES
            logger.info("QuickJS $version context created")
        }

    private companion object {
        const val THREAD_STACK_BYTES = 16L * 1024 * 1024
        const val QUICKJS_STACK_BYTES = 12L * 1024 * 1024

        // Upper bound against runaway scripts. Solving needs far less; see DEVICE-QUIRKS.md.
        const val DEFAULT_MEMORY_LIMIT = 256L * 1024 * 1024
    }
}
