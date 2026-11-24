// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.ByteRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures: core/innertube/src/test/resources/player/, recorded 2026-11-24 from the live
 * /youtubei/v1/player endpoint and sanitised (no session data, fake URLs and signatures; the
 * format lists and ranges are real). Video aqz-KE-bpKQ is "Big Buck Bunny 60fps 4K"
 * (Blender Foundation, CC BY 3.0).
 */
class PlayerResponseParserTest {
    @Test
    fun `tv_samsung response has direct URLs that need the n challenge`() {
        val response = parseFixture("tv_samsung-ok-n.json") as PlayerResponse.Playable

        assertEquals(634_709L, response.durationMs)
        assertTrue(response.formats.size > 30)
        assertTrue(response.formats.all { it.nChallenge == "FixtureNChallenge01" })
        assertTrue(response.formats.none { it.encryptedSignature != null })

        val h264_1080 = response.formats.single { it.itag == 299 }
        assertEquals("video/mp4", h264_1080.mimeType)
        assertTrue(h264_1080.codecs.startsWith("avc1."))
        assertEquals(1080, h264_1080.height)
        assertEquals(60, h264_1080.fps)
        assertTrue(h264_1080.initRange.end < h264_1080.indexRange.start)

        val aac = response.formats.single { it.itag == 140 }
        assertEquals("audio/mp4", aac.mimeType)
        assertEquals("mp4a.40.2", aac.codecs)
        assertEquals(44100, aac.audioSampleRate)
        assertEquals(2, aac.audioChannels)
    }

    @Test
    fun `codec families and heights the player can choose from`() {
        val formats = (parseFixture("tv_samsung-ok-n.json") as PlayerResponse.Playable).formats

        val families = formats.map { it.codecs.substringBefore('.') }.toSet()
        // Multichannel ec-3/ac-3/IAMF are in the response too; the player policy decides what to use.
        assertEquals(setOf("avc1", "vp9", "av01", "mp4a", "opus", "ec-3", "ac-3", "iamf"), families)
        assertEquals("each itag once (DRC and voice-boost copies dropped)", formats.size, formats.map { it.itag }.toSet().size)
        assertEquals(2160, formats.mapNotNull { it.height }.max())
    }

    @Test
    fun `ciphered response carries the encrypted signature and its parameter name`() {
        val formats = (parseFixture("tv_samsung-ok-cipher.json") as PlayerResponse.Playable).formats

        assertTrue(formats.isNotEmpty())
        assertTrue(formats.all { it.encryptedSignature != null && it.signatureParameter == "sig" })
        assertTrue(formats.all { it.nChallenge == "FixtureNChallenge01" })
        assertTrue(formats.all { it.url.host == "rr1---sn-fixture.googlevideo.com" })
        assertNull(formats.first().url.queryParameter("sig"))
    }

    @Test
    fun `visionos response needs no challenges`() {
        val formats = (parseFixture("visionos-ok.json") as PlayerResponse.Playable).formats

        assertTrue(formats.isNotEmpty())
        assertTrue(formats.all { it.nChallenge == null && it.encryptedSignature == null })
    }

    @Test
    fun `reload error from the tv client is unplayable and not a bot check`() {
        val response = parseFixture("tv-unplayable-reload.json") as PlayerResponse.Unplayable

        assertEquals("UNPLAYABLE", response.status)
        assertEquals("The page needs to be reloaded.", response.reason)
        assertFalse(response.isBotCheck)
    }

    @Test
    fun `missing visitor data shows up as a bot check`() {
        val response = parseFixture("tv_samsung-login-required.json") as PlayerResponse.Unplayable

        assertEquals("LOGIN_REQUIRED", response.status)
        assertTrue(response.isBotCheck)
    }

    @Test
    fun `formats without byte ranges, DRC and voice-boost audio and non-default audio tracks are left out`() {
        val body =
            """
            {"playabilityStatus":{"status":"OK"},"videoDetails":{"lengthSeconds":"10"},"_comment":"no approxDurationMs: falls back to lengthSeconds",
             "streamingData":{"adaptiveFormats":[
              {"itag":137,"mimeType":"video/mp4; codecs=\"avc1.640028\"","bitrate":1,"height":1080,
               "url":"https://example.invalid/a","initRange":{"start":"0","end":"9"},"indexRange":{"start":"10","end":"20"}},
              {"itag":136,"mimeType":"video/mp4; codecs=\"avc1.4d401f\"","bitrate":1,"url":"https://example.invalid/b"},
              {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":1,"isDrc":true,
               "url":"https://example.invalid/c","initRange":{"start":"0","end":"9"},"indexRange":{"start":"10","end":"20"}},
              {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":1,"isVb":true,
               "url":"https://example.invalid/v","initRange":{"start":"0","end":"9"},"indexRange":{"start":"10","end":"20"}},
              {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":1,"audioTrack":{"audioIsDefault":false},
               "url":"https://example.invalid/d","initRange":{"start":"0","end":"9"},"indexRange":{"start":"10","end":"20"}},
              {"itag":250,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":1,"audioTrack":{"audioIsDefault":true},
               "url":"https://example.invalid/e","initRange":{"start":"0","end":"9"},"indexRange":{"start":"10","end":"20"}}
             ]}}
            """.trimIndent()

        val response = PlayerResponseParser.parse(body) as PlayerResponse.Playable

        assertEquals(listOf(137, 250), response.formats.map { it.itag })
        assertEquals(ByteRange(0, 9), response.formats[0].initRange)
        assertEquals(10_000L, response.durationMs)
    }

    @Test
    fun `OK without usable formats is reported as unplayable`() {
        val response = PlayerResponseParser.parse("""{"playabilityStatus":{"status":"OK"},"streamingData":{}}""")

        assertEquals("no playable adaptive formats", (response as PlayerResponse.Unplayable).reason)
    }

    @Test(expected = InnertubeParseException::class)
    fun `non-JSON body is a parse error`() {
        PlayerResponseParser.parse("<html>")
    }
}

internal fun fixture(name: String): String =
    checkNotNull(PlayerResponseParserTest::class.java.getResourceAsStream("/player/$name")) { "no fixture $name" }
        .use { it.readBytes().toString(Charsets.UTF_8) }

private fun parseFixture(name: String): PlayerResponse = PlayerResponseParser.parse(fixture(name))
