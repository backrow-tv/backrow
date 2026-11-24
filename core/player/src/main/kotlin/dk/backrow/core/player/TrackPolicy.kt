// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import dk.backrow.core.model.AdaptiveFormat

/** How a device can decode one video format, as far as Android reports it. */
enum class DecoderKind { HARDWARE, SOFTWARE }

/** Answers "can this device decode this?" Implemented with Media3 on devices ([MediaCodecDecoderSupport]), faked in tests. */
fun interface DecoderSupport {
    /**
     * The best decoder for [mimeType] (e.g. `video/avc`) with this RFC 6381 [codecs] string
     * (which carries profile and level), size and frame rate; null if none can decode it.
     */
    fun decoderFor(mimeType: String, codecs: String, width: Int, height: Int, fps: Int): DecoderKind?
}

/** The formats to put in the manifest, and why. */
data class TrackSelection(
    val video: List<AdaptiveFormat>,
    val audio: List<AdaptiveFormat>,
    /** Codec family chosen for video, e.g. `avc1`; for logs and the quirks file. */
    val videoCodec: String,
    val videoDecoder: DecoderKind,
)

/**
 * Chooses which of a video's formats Backrow offers the player (CLAUDE.md Session 2 brief).
 *
 * Video, in order:
 * 1. Keep only formats a decoder on this device can handle: codec profile and level, size and
 *    frame rate, the same check ExoPlayer makes.
 *    VP9 and AV1 count only with a **hardware** decoder: software VP9/AV1 drops frames on TV
 *    hardware. H.264 may be software (emulators, some boxes) because every device has it.
 * 2. Cap the height at [maxHeight] (from the device's memory class, see [BufferPolicy]).
 * 3. Pick one codec family: the one that reaches the highest height under the cap; on a tie,
 *    H.264 before VP9 before AV1, because H.264 hardware decoders are the most mature on cheap
 *    TV chips. In practice: H.264 up to 1080p, VP9/AV1 only when they offer more.
 *
 * Audio: stereo AAC (`mp4a.40.*`) and Opus. Multichannel EC-3/AC-3/IAMF need passthrough
 * decisions that belong to the later player-polish slice, so they are left out for now.
 */
object TrackPolicy {
    private val VIDEO_FAMILIES = listOf("avc1" to "video/avc", "vp9" to "video/x-vnd.on2.vp9", "av01" to "video/av01")

    fun select(
        videoFormats: List<AdaptiveFormat>,
        audioFormats: List<AdaptiveFormat>,
        decoders: DecoderSupport,
        maxHeight: Int,
    ): TrackSelection? {
        val candidates =
            VIDEO_FAMILIES.mapNotNull { (family, decoderMime) ->
                var kind: DecoderKind? = null
                val usable =
                    videoFormats.filter { f ->
                        val h = f.height ?: return@filter false
                        val w = f.width ?: return@filter false
                        if (codecFamily(f.codecs) != family || h > maxHeight) return@filter false
                        val decoder = decoders.decoderFor(decoderMime, f.codecs, w, h, f.fps ?: 30) ?: return@filter false
                        val allowed = family == "avc1" || decoder == DecoderKind.HARDWARE
                        if (allowed && (kind == null || decoder == DecoderKind.HARDWARE)) kind = decoder
                        allowed
                    }
                if (usable.isEmpty()) null else Candidate(family, usable, checkNotNull(kind))
            }
        // maxByOrNull keeps the first of equal maxima, and candidates are in tie-break order.
        val best = candidates.maxByOrNull { c -> c.formats.maxOf { it.height ?: 0 } } ?: return null

        val audio = audioFormats.filter { isStereoAacOrOpus(it) }
        if (audio.isEmpty()) return null
        return TrackSelection(best.formats.sortedBy { it.bitrate }, audio.sortedBy { it.bitrate }, best.family, best.kind)
    }

    internal fun codecFamily(codecs: String): String = codecs.substringBefore('.').lowercase()

    private fun isStereoAacOrOpus(f: AdaptiveFormat): Boolean {
        val family = codecFamily(f.codecs)
        return (family == "mp4a" || family == "opus") && (f.audioChannels ?: 2) <= 2
    }

    private class Candidate(val family: String, val formats: List<AdaptiveFormat>, val kind: DecoderKind)
}
