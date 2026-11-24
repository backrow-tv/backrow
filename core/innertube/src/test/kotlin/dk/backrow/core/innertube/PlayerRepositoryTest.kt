// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PlayerRepositoryTest {
    private lateinit var youtube: FakeYouTube
    private lateinit var js: FakeJsRuntime

    @Before
    fun setUp() {
        youtube = FakeYouTube().start()
        js = FakeJsRuntime()
    }

    @After
    fun tearDown() = youtube.shutdownServer()

    private fun repository() =
        InnertubePlayerRepository(
            http = youtube.http(),
            solver = ChallengeSolver(youtube.http(), js, InMemoryPlayerJsCache(), NoOpLogger),
            logger = NoOpLogger,
        )

    @Test
    fun `tv_samsung streams come back with the n challenge solved`() =
        runTest {
            val info = repository().streams(VIDEO)

            assertEquals("tv_samsung", info.source)
            assertEquals(VIDEO, info.videoId)
            assertEquals(634_709L, info.durationMs)
            assertTrue(info.videoFormats.isNotEmpty() && info.audioFormats.isNotEmpty())
            (info.videoFormats + info.audioFormats).forEach {
                assertEquals("solved-FixtureNChallenge01", it.url.toHttpUrl().queryParameter("n"))
            }
            assertEquals(ClientProfiles.TV_SAMSUNG.userAgent, info.requestHeaders["User-Agent"])
        }

    @Test
    fun `player request carries the profile, visitor data and signature timestamp`() =
        runTest {
            repository().streams(VIDEO)

            val request = youtube.requests.single { it.url.encodedPath == "/youtubei/v1/player" }
            assertEquals("false", request.url.queryParameter("prettyPrint"))
            assertEquals("7", request.headers["X-YouTube-Client-Name"])
            assertEquals(ClientProfiles.TV_SAMSUNG.clientVersion, request.headers["X-YouTube-Client-Version"])
            assertEquals(FakeTv.VISITOR, request.headers["X-Goog-Visitor-Id"])
            assertEquals(ClientProfiles.TV_SAMSUNG.userAgent, request.headers["User-Agent"])

            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            val client = body["context"]!!.jsonObject["client"]!!.jsonObject
            assertEquals("TVHTML5", client["clientName"]!!.jsonPrimitive.content)
            assertEquals("Samsung", client["deviceMake"]!!.jsonPrimitive.content)
            assertEquals(FakeTv.VISITOR, client["visitorData"]!!.jsonPrimitive.content)
            assertEquals(VIDEO, body["videoId"]!!.jsonPrimitive.content)
            val playback = body["playbackContext"]!!.jsonObject["contentPlaybackContext"]!!.jsonObject
            assertEquals(FakeTv.STS, playback["signatureTimestamp"]!!.jsonPrimitive.int)
            assertEquals("true", body["contentCheckOk"]!!.jsonPrimitive.content)
        }

    @Test
    fun `ciphered formats get both the signature and n applied`() =
        runTest {
            youtube.playerFixtures = mapOf("TVHTML5" to "tv_samsung-ok-cipher.json")

            val info = repository().streams(VIDEO)

            val url = info.videoFormats.first().url.toHttpUrl()
            assertTrue(url.queryParameter("sig")!!.startsWith("sig-"))
            assertEquals("solved-FixtureNChallenge01", url.queryParameter("n"))
        }

    @Test
    fun `falls back to visionos when tv_samsung is unplayable`() =
        runTest {
            youtube.playerFixtures = mapOf("TVHTML5" to "tv-unplayable-reload.json", "VISIONOS" to "visionos-ok.json")

            val info = repository().streams(VIDEO)

            assertEquals("visionos", info.source)
            assertNull(info.videoFormats.first().url.toHttpUrl().queryParameter("n"))
            assertEquals(ClientProfiles.VISIONOS.userAgent, info.requestHeaders["User-Agent"])
        }

    @Test
    fun `falls back to visionos when the solver fails`() =
        runTest {
            js.solveN = { null }

            val info = repository().streams(VIDEO)

            assertEquals("visionos", info.source)
        }

    @Test
    fun `fails with every attempt listed, and starts a new session after a bot check`() =
        runTest {
            youtube.playerFixtures = emptyMap() // every client gets LOGIN_REQUIRED
            val repository = repository()

            try {
                repository.streams(VIDEO)
                fail("expected PlaybackUnavailableException")
            } catch (e: PlaybackUnavailableException) {
                assertEquals(2, e.attempts.size)
                assertTrue(e.attempts[0].startsWith("tv_samsung: LOGIN_REQUIRED"))
                assertTrue(e.attempts[1].startsWith("visionos: LOGIN_REQUIRED"))
            }
            assertEquals(1, youtube.count("/tv"))

            youtube.playerFixtures = mapOf("TVHTML5" to "tv_samsung-ok-n.json")
            repository.streams(VIDEO)
            assertEquals(2, youtube.count("/tv"))
        }

    @Test
    fun `warm-up fetches the session and player once, so streams only asks for the video`() =
        runTest {
            val repository = repository()

            repository.warmUp()
            val before = youtube.requests.size
            repository.streams(VIDEO)

            assertEquals(2, before) // /tv and the player JS
            assertEquals(listOf("/youtubei/v1/player"), youtube.requests.drop(before).map { it.url.encodedPath })
        }

    @Test
    fun `warm-up never throws`() =
        runTest {
            youtube.tvPageHtml = "<html></html>"
            repository().warmUp()
        }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects something that is not a video id`() =
        runTest {
            repository().streams("../../etc")
        }

    private companion object {
        const val VIDEO = "aqz-KE-bpKQ"
    }
}
