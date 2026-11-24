// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube.live

import dk.backrow.core.innertube.JsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.Writer

/**
 * A [JsRuntime] backed by a long-lived `node` process, for the live canary only (never shipped:
 * the app uses QuickJS from :core:jsengine, ADR-0009). node is preinstalled on GitHub's runners,
 * so the canary can run the real EJS solver on the JVM without a new dependency or an emulator.
 *
 * Protocol (see node-js-runtime.js): one JSON-encoded script per line on stdin, one JSON reply per
 * line on stdout. The node executable is `node` on the PATH, or `BACKROW_NODE` if set.
 */
internal class NodeJsRuntime : JsRuntime, AutoCloseable {
    private val mutex = Mutex()
    private var process: Process? = null
    private var input: Writer? = null
    private var output: BufferedReader? = null

    override suspend fun evaluate(script: String): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (process?.isAlive != true) start()
                val writer = checkNotNull(input)
                writer.write(JsonPrimitive(script).toString())
                writer.write("\n")
                writer.flush()
                val line = checkNotNull(output).readLine() ?: error("node exited (code ${process?.waitFor()})")
                val reply = Json.parseToJsonElement(line).jsonObject
                if (reply.getValue("ok").jsonPrimitive.boolean) {
                    reply.getValue("value").jsonPrimitive.content
                } else {
                    throw IllegalStateException("JS error: ${reply["error"]?.jsonPrimitive?.contentOrNull}")
                }
            }
        }

    override suspend fun reset() = mutex.withLock { withContext(Dispatchers.IO) { stop() } }

    override fun close() = stop()

    private fun start() {
        val node = System.getenv("BACKROW_NODE")?.takeIf { it.isNotBlank() } ?: "node"
        val p =
            ProcessBuilder(node, shim.absolutePath)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        process = p
        input = p.outputStream.bufferedWriter(Charsets.UTF_8)
        output = p.inputStream.bufferedReader(Charsets.UTF_8)
    }

    private fun stop() {
        runCatching { input?.close() }
        process?.let { if (!it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) it.destroyForcibly() }
        process = null
        input = null
        output = null
    }

    private companion object {
        /** node needs a file to run, so the shim is copied out of the test resources once. */
        val shim: File by lazy {
            val bytes =
                checkNotNull(NodeJsRuntime::class.java.getResourceAsStream("node-js-runtime.js")) {
                    "missing node-js-runtime.js"
                }.use { it.readBytes() }
            File.createTempFile("backrow-node-js-runtime", ".js").apply {
                deleteOnExit()
                writeBytes(bytes)
            }
        }
    }
}
