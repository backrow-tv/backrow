// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube.live

import dk.backrow.core.innertube.ChallengeSolver
import dk.backrow.core.innertube.InMemoryPlayerJsCache
import dk.backrow.core.innertube.InnertubeHttp
import dk.backrow.core.model.Logger
import okhttp3.OkHttpClient
import org.junit.Assume
import java.util.concurrent.TimeUnit

/**
 * What the live canary tests share: one HTTP client, one node process and one solver, so the
 * player JS is downloaded and preprocessed once per run. These tests talk to the real YouTube
 * and run only with `BACKROW_LIVE_TESTS=1` (CLAUDE.md rule 6), in the nightly canary.
 */
internal object LiveYouTube {
    /**
     * A public, stable, CC-BY video: "Caminandes 3: Llamigos" (Blender Foundation), 24 fps up to
     * 1080p. The same one the app plays.
     */
    const val VIDEO_ID = "SkVqJ1SGeL0"

    private val enabled = System.getenv("BACKROW_LIVE_TESTS") == "1"

    fun assumeEnabled() = Assume.assumeTrue("live YouTube tests run only with BACKROW_LIVE_TESTS=1", enabled)

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val http: InnertubeHttp by lazy { InnertubeHttp(client) }

    private val runtime: NodeJsRuntime by lazy {
        NodeJsRuntime().also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
    }

    val solver: ChallengeSolver by lazy { ChallengeSolver(http, runtime, InMemoryPlayerJsCache(), RedactingLogger) }
}

/**
 * Prints the production code's log lines so the canary's output shows timings and which step
 * failed. Production code never logs visitor data or stream URLs, but stream URLs embed the
 * runner's IP address, so anything URL-shaped is blanked out anyway.
 */
internal object RedactingLogger : Logger {
    override fun debug(message: String) = print("DEBUG", message, null)

    override fun info(message: String) = print("INFO", message, null)

    override fun warn(message: String, throwable: Throwable?) = print("WARN", message, throwable)

    override fun error(message: String, throwable: Throwable?) = print("ERROR", message, throwable)

    private fun print(level: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " (${it.javaClass.simpleName}: ${it.message})" }.orEmpty()
        println("[live] $level ${redact(message + suffix)}")
    }

    fun redact(text: String): String = URL_LIKE.replace(text, "<redacted>")

    private val URL_LIKE = Regex("""(?:https?://|[\w.-]+\.googlevideo\.com)\S*""")
}
