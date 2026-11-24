// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import dk.backrow.core.player.DecoderKind.HARDWARE
import dk.backrow.core.player.DecoderKind.SOFTWARE
import dk.backrow.core.player.TestFormats.aac
import dk.backrow.core.player.TestFormats.allAudio
import dk.backrow.core.player.TestFormats.allVideo
import dk.backrow.core.player.TestFormats.av1_2160
import dk.backrow.core.player.TestFormats.avc1080
import dk.backrow.core.player.TestFormats.avc480
import dk.backrow.core.player.TestFormats.avc720
import dk.backrow.core.player.TestFormats.opus
import dk.backrow.core.player.TestFormats.vp9_1440
import dk.backrow.core.player.TestFormats.vp9_2160
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPolicyTest {
    private val avc = "video/avc"
    private val vp9 = "video/x-vnd.on2.vp9"
    private val av1 = "video/av01"

    @Test
    fun `H_264 wins up to 1080p even when VP9 hardware exists`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 2160), vp9 to (HARDWARE to 2160)))

        val s = TrackPolicy.select(allVideo, allAudio, device, maxHeight = 1080)!!

        assertEquals("avc1", s.videoCodec)
        assertEquals(listOf(avc480, avc720, avc1080), s.video)
    }

    @Test
    fun `VP9 hardware is chosen when it offers more than H_264`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 2160), vp9 to (HARDWARE to 2160)))

        val s = TrackPolicy.select(allVideo, allAudio, device, maxHeight = 2160)!!

        assertEquals("vp9", s.videoCodec)
        assertEquals(2160, s.video.maxOf { it.height!! })
    }

    @Test
    fun `software VP9 and AV1 are never used`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 1080), vp9 to (SOFTWARE to 2160), av1 to (SOFTWARE to 2160)))

        val s = TrackPolicy.select(allVideo, allAudio, device, maxHeight = 2160)!!

        assertEquals("avc1", s.videoCodec)
        assertEquals(1080, s.video.maxOf { it.height!! })
    }

    @Test
    fun `software H_264 is allowed, as on the emulators`() {
        val device = FakeDecoders(mapOf(avc to (SOFTWARE to 1080)))

        val s = TrackPolicy.select(allVideo, allAudio, device, maxHeight = 1080)!!

        assertEquals(SOFTWARE, s.videoDecoder)
        assertEquals(avc1080, s.video.last())
    }

    @Test
    fun `decoder limits cap the height`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 720)))

        assertEquals(avc720, TrackPolicy.select(allVideo, allAudio, device, maxHeight = 2160)!!.video.last())
    }

    @Test
    fun `a refused H_264 profile makes VP9 win at 1080p, as on the API 36 emulator`() {
        // Seen 2026-11-24: c2.goldfish.h264.decoder decodes 720p60 Main but not 1080p60 High@4.2.
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 2160), vp9 to (HARDWARE to 2160)), refusedCodecs = setOf("avc1.64002a"))

        val s = TrackPolicy.select(allVideo, allAudio, device, maxHeight = 1080)!!

        assertEquals("vp9", s.videoCodec)
        assertEquals(1080, s.video.maxOf { it.height!! })
    }

    @Test
    fun `AV1 hardware is used only when nothing else reaches as high`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 1080), av1 to (HARDWARE to 2160)))

        val s = TrackPolicy.select(listOf(avc1080, vp9_1440, vp9_2160, av1_2160), allAudio, device, maxHeight = 2160)!!

        assertEquals("av01", s.videoCodec)
    }

    @Test
    fun `audio is stereo AAC and Opus only`() {
        val device = FakeDecoders(mapOf(avc to (HARDWARE to 1080)))

        assertEquals(listOf(aac, opus), TrackPolicy.select(allVideo, allAudio, device, maxHeight = 1080)!!.audio)
    }

    @Test
    fun `nothing decodable gives null`() {
        assertNull(TrackPolicy.select(allVideo, allAudio, FakeDecoders(emptyMap()), maxHeight = 1080))
        assertNull(TrackPolicy.select(allVideo, listOf(TestFormats.ec3), FakeDecoders(mapOf(avc to (HARDWARE to 1080))), 1080))
    }
}
