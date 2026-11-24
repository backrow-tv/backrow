// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import dk.backrow.core.model.AdaptiveFormat
import dk.backrow.core.model.ByteRange

/**
 * A subset of the format list YouTube returned for aqz-KE-bpKQ on 2026-11-24 (the same data as
 * core/innertube/src/test/resources/player/tv_samsung-ok-n.json), with fake URLs.
 */
internal object TestFormats {
    private fun video(itag: Int, mime: String, codecs: String, w: Int, h: Int, fps: Int, bitrate: Int) =
        AdaptiveFormat(
            itag = itag, mimeType = mime, codecs = codecs, bitrate = bitrate, width = w, height = h, fps = fps,
            initRange = ByteRange(0, 739), indexRange = ByteRange(740, 2299),
            url = "https://rr1---sn-fixture.googlevideo.com/videoplayback?itag=$itag&n=solved&sig=a/b+c",
        )

    private fun audio(itag: Int, mime: String, codecs: String, channels: Int, rate: Int, bitrate: Int) =
        AdaptiveFormat(
            itag = itag, mimeType = mime, codecs = codecs, bitrate = bitrate, audioSampleRate = rate, audioChannels = channels,
            initRange = ByteRange(0, 631), indexRange = ByteRange(632, 1435),
            url = "https://rr1---sn-fixture.googlevideo.com/videoplayback?itag=$itag&n=solved",
        )

    val avc720 = video(298, "video/mp4", "avc1.4d4020", 1280, 720, 60, 3_400_000)
    val avc1080 = video(299, "video/mp4", "avc1.64002a", 1920, 1080, 60, 5_700_000)
    val avc480 = video(135, "video/mp4", "avc1.4d401f", 854, 480, 30, 1_100_000)
    val vp9_1080 = video(303, "video/webm", "vp9", 1920, 1080, 60, 4_300_000)
    val vp9_1440 = video(308, "video/webm", "vp9", 2560, 1440, 60, 12_000_000)
    val vp9_2160 = video(315, "video/webm", "vp9", 3840, 2160, 60, 25_000_000)
    val av1_2160 = video(401, "video/mp4", "av01.0.13M.08", 3840, 2160, 60, 17_000_000)
    val aac = audio(140, "audio/mp4", "mp4a.40.2", 2, 44100, 131_000)
    val opus = audio(251, "audio/webm", "opus", 2, 48000, 143_000)
    val ec3 = audio(328, "audio/mp4", "ec-3", 6, 48000, 385_000)

    val allVideo = listOf(avc480, avc720, avc1080, vp9_1080, vp9_1440, vp9_2160, av1_2160)
    val allAudio = listOf(aac, opus, ec3)
}

/**
 * A device described by which codec families it decodes in hardware/software, up to a height,
 * optionally refusing some codec strings (as a decoder refuses a profile/level it lacks).
 */
internal class FakeDecoders(
    private val support: Map<String, Pair<DecoderKind, Int>>,
    private val refusedCodecs: Set<String> = emptySet(),
) : DecoderSupport {
    override fun decoderFor(mimeType: String, codecs: String, width: Int, height: Int, fps: Int): DecoderKind? =
        if (codecs in refusedCodecs) null else support[mimeType]?.takeIf { height <= it.second }?.first
}
