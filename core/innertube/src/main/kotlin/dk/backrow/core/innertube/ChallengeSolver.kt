// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The player JS could not be fetched, preprocessed or run. */
class ChallengeSolverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Solved values, keyed by the challenge they answer. */
internal class Solutions(val n: Map<String, String>, val sig: Map<String, String>)

/**
 * Solves the player-JS challenges in stream URLs (ADR-0009): the `n` query parameter and the
 * `s` value of a `signatureCipher`.
 *
 * It runs yt-dlp's EJS solver (`yt.solver.lib.min.js` + `yt.solver.core.min.js`, bundled from
 * the yt-dlp/ejs 0.8.0 release) through [JsRuntime], using only the solver's public input and
 * output format. Based on reference/yt-dlp/yt_dlp/extractor/youtube/jsc/_builtin/ejs.py
 * (`_construct_stdin`, `_real_bulk_solve`) and `_video.py` (`_load_player`,
 * `_extract_signature_timestamp`, `_PLAYER_JS_VARIANT_MAP`).
 *
 * Expensive work happens once per player version: downloading the player JS and preprocessing
 * it (about 10 s in QuickJS on the API 36 emulator). The preprocessed player is cached on disk
 * ([PlayerJsCache]). Loading it turns it into the `n` and `sig` functions once, the way the
 * solver's own `getFromPrepared` does (yt.solver.core.js 0.8.0), and keeps those functions in
 * the JS context; each video then costs only the function calls. Going through `jsc()` for
 * every video would re-run the 4 MB preprocessed player each time (~0.8 s on the emulator).
 */
internal class ChallengeSolver(
    private val http: InnertubeHttp,
    private val runtime: JsRuntime,
    private val cache: PlayerJsCache,
    private val logger: Logger,
    private val clock: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var preludeLoaded = false
    private var loadedPlayerId: String? = null
    private var loadedSignatureTimestamp: Int = 0

    /**
     * Makes [playerId] the loaded player and returns its signature timestamp, which the player
     * request must send. Downloads and preprocesses the player only if no cache has it.
     */
    suspend fun preparePlayer(playerId: String): Int =
        mutex.withLock {
            if (loadedPlayerId == playerId) return loadedSignatureTimestamp
            loadPrelude()
            val cached = cache.get(playerId)
            val sts =
                if (cached != null) {
                    evaluate("globalThis.$PLAYER_GLOBAL = ${jsString(cached.preprocessedPlayer)}; 0")
                    cached.signatureTimestamp
                } else {
                    preprocess(playerId)
                }
            val ms = timed { loadSolverFunctions() }
            logger.info("Player $playerId ${if (cached != null) "loaded from cache" else "ready"}; solver functions in $ms ms")
            loadedPlayerId = playerId
            loadedSignatureTimestamp = sts
            sts
        }

    /** Solves the given challenges against the player loaded by [preparePlayer]. */
    suspend fun solve(nChallenges: Set<String>, sigChallenges: Set<String>): Solutions =
        mutex.withLock {
            check(loadedPlayerId != null) { "preparePlayer() first" }
            if (nChallenges.isEmpty() && sigChallenges.isEmpty()) return Solutions(emptyMap(), emptyMap())
            val input =
                buildJsonObject {
                    putJsonArray("n") { nChallenges.forEach { add(it) } }
                    putJsonArray("sig") { sigChallenges.forEach { add(it) } }
                }
            val script = "$SOLVE_FUNCTION($input)"
            var output = ""
            val ms = timed { output = evaluate(script) }
            logger.info("Solved ${nChallenges.size} n and ${sigChallenges.size} sig challenges in $ms ms")
            val result = parseOutput(output)
            val responses = result.responses
            if (responses.size != 2) throw ChallengeSolverException("solver returned ${responses.size} responses")
            Solutions(n = responses[0].dataOrThrow("n"), sig = responses[1].dataOrThrow("sig"))
        }

    private suspend fun loadPrelude() {
        if (preludeLoaded) return
        val ms =
            timed {
                evaluate("${EjsScripts.lib}\nObject.assign(globalThis, lib);\n${EjsScripts.core}\ntypeof jsc")
                    .also { if (it != "function") throw ChallengeSolverException("solver did not define jsc: $it") }
            }
        logger.info("Challenge solver (EJS ${EjsScripts.VERSION}) loaded in $ms ms")
        preludeLoaded = true
    }

    /**
     * Turns the loaded preprocessed player into its `n` and `sig` functions (yt.solver.core.js
     * 0.8.0 `getFromPrepared`) and drops the 4 MB source string, which is no longer needed.
     */
    private suspend fun loadSolverFunctions() {
        val kinds =
            evaluate(
                "(function () { var r = { n: null, sig: null }; Function('_result', globalThis.$PLAYER_GLOBAL)(r);" +
                    " globalThis.$SOLVERS_GLOBAL = r; globalThis.$PLAYER_GLOBAL = null; return typeof r.n + ' ' + typeof r.sig; })()",
            )
        if (kinds != "function function") logger.warn("Solver functions incomplete: $kinds")
    }

    private suspend fun preprocess(playerId: String): Int {
        val path = "s/player/$playerId/$PLAYER_PATH"
        var code = ""
        val downloadMs = timed { code = http.get(path, mapOf("User-Agent" to ClientProfiles.TV_PAGE_USER_AGENT)) }
        val sts =
            STS.find(code)?.groupValues?.get(1)?.toInt()
                ?: throw InnertubeParseException("no signature timestamp in player $playerId")
        logger.info("Player $playerId downloaded (${code.length / 1024} KiB) in $downloadMs ms, sts=$sts")

        val input =
            buildJsonObject {
                put("type", "player")
                put("player", code)
                putJsonArray("requests") {}
                put("output_preprocessed", true)
            }
        var status = ""
        val preprocessMs =
            timed {
                status =
                    evaluate(
                        "(function () { var out = jsc($input); if (out.type !== 'result') return JSON.stringify(out);" +
                            " globalThis.$PLAYER_GLOBAL = out.preprocessed_player; return 'ok'; })()",
                    )
            }
        if (status != "ok") {
            parseOutput(status) // throws with the solver's error message
            throw ChallengeSolverException("preprocessing failed")
        }
        logger.info("Player $playerId preprocessed in $preprocessMs ms")
        val preprocessed = evaluate("globalThis.$PLAYER_GLOBAL")
        runCatching { cache.put(playerId, CachedPlayer(sts, preprocessed)) }
            .onFailure { logger.warn("Could not cache player $playerId", it) }
        return sts
    }

    private suspend fun evaluate(script: String): String =
        try {
            runtime.evaluate(script)
        } catch (e: ChallengeSolverException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw ChallengeSolverException("JS engine failed: ${e.message}", e)
        }

    private fun parseOutput(output: String): SolverOutput {
        val parsed =
            runCatching { json.decodeFromString<SolverOutput>(output) }
                .getOrElse { throw ChallengeSolverException("unreadable solver output", it) }
        if (parsed.type != "result") throw ChallengeSolverException("solver error: ${parsed.error}")
        return parsed
    }

    private fun SolverResponse.dataOrThrow(kind: String): Map<String, String> =
        if (type == "result") data else throw ChallengeSolverException("$kind solver error: $error")

    private inline fun timed(block: () -> Unit): Long {
        val start = clock()
        block()
        return (clock() - start) / 1_000_000
    }

    @Serializable
    private class SolverOutput(val type: String, val error: String? = null, val responses: List<SolverResponse> = emptyList())

    @Serializable
    private class SolverResponse(val type: String, val error: String? = null, val data: Map<String, String> = emptyMap())

    companion object {
        /**
         * The TV build of the player, yt-dlp `_PLAYER_JS_VARIANT_MAP['tv']`; the one YouTube's own
         * /tv page loads. yt-dlp defaults to the `main` build, but on 2026-11-24 EJS 0.8.0 failed
         * to preprocess the main build of the new player 4ee1fa28 ("addOnDisposeCallback is not a
         * function") while the TV build worked, and both gave identical answers on 7f755914.
         * See docs/INNERTUBE-CHANGELOG.md.
         */
        const val PLAYER_PATH = "tv-player-ias.vflset/tv-player-ias.js"

        // yt-dlp _video.py _extract_signature_timestamp
        private val STS = Regex("""(?:signatureTimestamp|sts)\s*:\s*([0-9]{5})""")
        private const val PLAYER_GLOBAL = "__backrowPreprocessedPlayer"
        private const val SOLVERS_GLOBAL = "__backrowSolvers"

        /**
         * Calls the loaded solver functions for `{n: [...], sig: [...]}` and answers in the
         * format of the EJS solver's `main` (yt.solver.core.js 0.8.0): one result or error
         * response per type, in the order n, sig.
         */
        private const val SOLVE_FUNCTION =
            "(function (input) {" +
                " var solvers = globalThis.$SOLVERS_GLOBAL;" +
                " function run(type, challenges) {" +
                "  if (!challenges.length) return { type: 'result', data: {} };" +
                "  var solver = solvers && solvers[type];" +
                "  if (!solver) return { type: 'error', error: 'Failed to extract ' + type + ' function' };" +
                "  try { var data = {}; challenges.forEach(function (c) { data[c] = solver(c); }); return { type: 'result', data: data }; }" +
                "  catch (e) { return { type: 'error', error: String(e) }; }" +
                " }" +
                " return JSON.stringify({ type: 'result', responses: [run('n', input.n), run('sig', input.sig)] });" +
                "})"
        private val json = Json { ignoreUnknownKeys = true }

        private fun jsString(value: String): String = JsonPrimitive(value).toString()
    }
}

/** The bundled EJS solver scripts (ADR-0009). Unmodified release files; see EjsScriptsTest. */
internal object EjsScripts {
    const val VERSION = "0.8.0"
    val lib: String by lazy { load("yt.solver.lib.min.js") }
    val core: String by lazy { load("yt.solver.core.min.js") }

    fun bytes(name: String): ByteArray =
        checkNotNull(EjsScripts::class.java.getResourceAsStream("/dk/backrow/core/innertube/ejs/$name")) {
            "missing bundled script $name"
        }.use { it.readBytes() }

    private fun load(name: String): String = bytes(name).toString(Charsets.UTF_8)
}
