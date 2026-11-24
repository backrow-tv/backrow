// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube.live

import dk.backrow.core.innertube.ClientProfile
import dk.backrow.core.innertube.ClientProfiles
import dk.backrow.core.innertube.InnertubePlayerRepository
import dk.backrow.core.model.AdaptiveFormat
import dk.backrow.core.model.StreamInfo
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/**
 * Canary for playback, once per profile in [ClientProfiles.PLAYBACK_CHAIN] (a profile added to the
 * chain is covered automatically): a /youtubei/v1/player request through that profile alone, then
 * the first kilobyte of one solved video and one audio stream URL.
 */
@RunWith(Parameterized::class)
class LivePlaybackTest(private val profileKey: String) {
    @get:Rule val timeout: Timeout = Timeout.seconds(180)

    private val profile: ClientProfile = ClientProfiles.PLAYBACK_CHAIN.single { it.key == profileKey }

    @Before fun live() = LiveYouTube.assumeEnabled()

    @Test
    fun `player request returns playable streams up to 1080p`() {
        val info = streams(profile)
        assertEquals(profile.key, info.source)
        assertTrue("no audio formats", info.audioFormats.isNotEmpty())
        val heights = info.videoFormats.mapNotNull { it.height }.toSortedSet()
        RedactingLogger.info("${profile.key}: ${info.videoFormats.size} video (heights $heights), ${info.audioFormats.size} audio")
        assertTrue("no 1080p video format; heights $heights", 1080 in heights)
    }

    @Test
    fun `a solved video and audio stream URL serve their first kilobyte`() {
        val info = streams(profile)
        val video = info.videoFormats.filter { it.height == 1080 }.minByOrNull { it.bitrate } ?: info.videoFormats.first()
        val audio = info.audioFormats.minBy { it.bitrate }
        fetchFirstKilobyte(info, video)
        fetchFirstKilobyte(info, audio)
    }

    /**
     * Asks for bytes 0–1023 the way Media3 does, with a Range header and exactly the headers
     * :core:innertube supplies. Expects 206 (or 200 from a server that ignores Range); an
     * unsolved `n` or signature gives 403. Failure messages never contain the URL, which embeds
     * the runner's IP address.
     */
    private fun fetchFirstKilobyte(info: StreamInfo, format: AdaptiveFormat) {
        val request =
            Request.Builder()
                .url(format.url)
                .apply { info.requestHeaders.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-1023")
                .build()
        val what = "${profile.key} itag ${format.itag}"
        try {
            LiveYouTube.client.newCall(request).execute().use { response ->
                assertTrue("HTTP ${response.code} for $what", response.code == 200 || response.code == 206)
                val bytes = response.body.byteStream().readNBytes(1024)
                assertTrue("empty body for $what", bytes.isNotEmpty())
                RedactingLogger.info("$what: HTTP ${response.code}, ${bytes.size} bytes")
            }
        } catch (e: IOException) {
            fail("${e.javaClass.simpleName} fetching $what")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun profiles(): List<String> = ClientProfiles.PLAYBACK_CHAIN.map { it.key }

        private val streams = mutableMapOf<String, Result<StreamInfo>>()

        /** One player request per profile per run, shared by both tests. */
        @Synchronized
        private fun streams(profile: ClientProfile): StreamInfo =
            streams.getOrPut(profile.key) {
                runCatching {
                    runBlocking {
                        InnertubePlayerRepository(
                            http = LiveYouTube.http,
                            solver = LiveYouTube.solver,
                            logger = RedactingLogger,
                            chain = listOf(profile),
                        ).streams(LiveYouTube.VIDEO_ID)
                    }
                }
            }.getOrElse { e -> throw AssertionError(RedactingLogger.redact("${profile.key}: ${e.message}"), e) }
    }
}
