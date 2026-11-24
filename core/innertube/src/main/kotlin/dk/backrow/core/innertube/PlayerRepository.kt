// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.AdaptiveFormat
import dk.backrow.core.model.Logger
import dk.backrow.core.model.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Finds playable streams for a video, anonymously. */
interface PlayerRepository {
    /** Returns the streams for [videoId], or throws [PlaybackUnavailableException]. */
    suspend fun streams(videoId: String): StreamInfo

    /**
     * Does the per-session and per-player-version work ahead of time (visitor data, player JS,
     * solver), so the first [streams] call is fast. Safe to call more than once; never throws.
     */
    suspend fun warmUp()
}

/** No client profile in the chain could play the video. [attempts] says why, one line per profile. */
class PlaybackUnavailableException(val videoId: String, val attempts: List<String>) :
    RuntimeException("No playable streams for $videoId: ${attempts.joinToString("; ")}")

/** Builds the app's [PlayerRepository]. */
object Innertube {
    fun playerRepository(jsRuntime: JsRuntime, cacheDirectory: File, logger: Logger): PlayerRepository {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        val http = InnertubeHttp(client)
        return InnertubePlayerRepository(
            http = http,
            solver = ChallengeSolver(http, jsRuntime, FilePlayerJsCache(File(cacheDirectory, "player-js")), logger),
            logger = logger,
        )
    }
}

/**
 * Tries [chain] in order (ADR-0005 §2) and returns the first profile's streams that are
 * playable, with every URL already transformed.
 */
internal class InnertubePlayerRepository(
    private val http: InnertubeHttp,
    private val solver: ChallengeSolver,
    private val logger: Logger,
    private val chain: List<ClientProfile> = ClientProfiles.PLAYBACK_CHAIN,
) : PlayerRepository {
    private val sessionMutex = Mutex()
    private var session: TvSession? = null

    override suspend fun warmUp() {
        try {
            val s = session()
            if (chain.any { it.requiresPlayerJs }) solver.preparePlayer(s.playerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Warm-up failed: ${e.message}")
        }
    }

    override suspend fun streams(videoId: String): StreamInfo {
        require(VIDEO_ID.matches(videoId)) { "not a video id" }
        val attempts = mutableListOf<String>()
        var botCheckSeen = false
        for (profile in chain) {
            try {
                when (val result = tryProfile(profile, videoId)) {
                    is Attempt.Success -> {
                        logger.info("Streams for $videoId from ${profile.key}: ${result.info.videoFormats.size} video, ${result.info.audioFormats.size} audio")
                        return result.info
                    }
                    is Attempt.Failure -> {
                        if (result.botCheck) botCheckSeen = true
                        attempts += "${profile.key}: ${result.reason}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempts += "${profile.key}: ${e.javaClass.simpleName}: ${e.message}"
            }
            logger.warn("Profile ${profile.key} failed for $videoId: ${attempts.last()}")
        }
        // A bot check on every profile usually means the visitor data went stale; the next call starts a new session.
        if (botCheckSeen) sessionMutex.withLock { session = null }
        throw PlaybackUnavailableException(videoId, attempts)
    }

    private sealed interface Attempt {
        class Success(val info: StreamInfo) : Attempt

        class Failure(val reason: String, val botCheck: Boolean = false) : Attempt
    }

    private suspend fun tryProfile(profile: ClientProfile, videoId: String): Attempt {
        val s = session()
        val sts = if (profile.requiresPlayerJs) solver.preparePlayer(s.playerId) else null
        val body = playerRequestBody(profile, videoId, s.visitorData, sts).toString()
        val response =
            PlayerResponseParser.parse(
                http.postJson("youtubei/v1/player", mapOf("prettyPrint" to "false"), body, playerRequestHeaders(profile, s.visitorData)),
            )
        return when (response) {
            is PlayerResponse.Unplayable -> Attempt.Failure("${response.status} ${response.reason.orEmpty()}".trim(), response.isBotCheck)
            is PlayerResponse.Playable -> {
                val formats = resolveUrls(profile, response.formats)
                val video = formats.filter { it.isVideo }
                val audio = formats.filter { it.isAudio }
                if (video.isEmpty() || audio.isEmpty()) {
                    Attempt.Failure("no usable video or audio formats")
                } else {
                    Attempt.Success(
                        StreamInfo(
                            videoId = videoId,
                            durationMs = response.durationMs,
                            videoFormats = video,
                            audioFormats = audio,
                            requestHeaders = mapOf("User-Agent" to profile.userAgent),
                            source = profile.key,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Applies the solved `n` and signature values to every URL (yt-dlp `_video.py`, the
     * "signature" and "n challenge" steps of format extraction). A format whose challenge the
     * solver could not answer is dropped, as yt-dlp does.
     */
    private suspend fun resolveUrls(profile: ClientProfile, formats: List<RawFormat>): List<AdaptiveFormat> {
        val nChallenges = formats.mapNotNullTo(mutableSetOf()) { it.nChallenge }
        val sigChallenges = formats.mapNotNullTo(mutableSetOf()) { it.encryptedSignature }
        val needsSolving = nChallenges.isNotEmpty() || sigChallenges.isNotEmpty()
        if (needsSolving && !profile.requiresPlayerJs) {
            logger.warn("${profile.key} unexpectedly sent challenges; dropping those formats")
        }
        val solutions =
            if (needsSolving && profile.requiresPlayerJs) solver.solve(nChallenges, sigChallenges) else Solutions(emptyMap(), emptyMap())

        return formats.mapNotNull { f ->
            val builder = f.url.newBuilder()
            f.nChallenge?.let { n -> builder.setQueryParameter("n", solutions.n[n] ?: return@mapNotNull null) }
            f.encryptedSignature?.let { s -> builder.setQueryParameter(f.signatureParameter, solutions.sig[s] ?: return@mapNotNull null) }
            AdaptiveFormat(
                itag = f.itag,
                mimeType = f.mimeType,
                codecs = f.codecs,
                bitrate = f.bitrate,
                width = f.width,
                height = f.height,
                fps = f.fps,
                audioSampleRate = f.audioSampleRate,
                audioChannels = f.audioChannels,
                initRange = f.initRange,
                indexRange = f.indexRange,
                contentLength = f.contentLength,
                url = builder.build().toString(),
            )
        }
    }

    private suspend fun session(): TvSession =
        sessionMutex.withLock {
            session ?: run {
                val html = http.get("tv", mapOf("User-Agent" to ClientProfiles.TV_PAGE_USER_AGENT))
                TvPageParser.parse(html).also {
                    session = it
                    logger.info("New anonymous session, player ${it.playerId}")
                }
            }
        }

    private companion object {
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
