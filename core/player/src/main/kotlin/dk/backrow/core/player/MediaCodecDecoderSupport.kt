// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * [DecoderSupport] that asks Media3 the same question ExoPlayer asks before it selects a track:
 * `MediaCodecInfo.isFormatSupported`, which checks size, frame rate *and* the codec profile and
 * level from the codec string. Checking only size and frame rate is not enough: on the API 36
 * emulator the H.264 decoder handles 1080p60 but not High@4.2 (`avc1.64002a`), so ExoPlayer
 * refused YouTube's 1080p H.264 stream (2026-11-24, see DEVICE-QUIRKS.md).
 *
 * "Hardware" is Media3's `hardwareAccelerated` (the platform flag on API 29+, Media3's name-based
 * heuristic before). Results are cached; the codec list does not change while the app runs.
 */
@OptIn(UnstableApi::class)
class MediaCodecDecoderSupport(private val context: Context) : DecoderSupport {
    private val cache = mutableMapOf<String, DecoderKind?>()

    @Synchronized
    override fun decoderFor(mimeType: String, codecs: String, width: Int, height: Int, fps: Int): DecoderKind? =
        cache.getOrPut("$mimeType $codecs $width $height $fps") {
            val format =
                Format.Builder()
                    .setSampleMimeType(mimeType)
                    .setCodecs(codecs)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(fps.toFloat())
                    .build()
            val supporting =
                runCatching { MediaCodecUtil.getDecoderInfos(mimeType, false, false) }
                    .getOrDefault(emptyList())
                    .filter { info -> runCatching { info.isFormatSupported(context, format) }.getOrDefault(false) }
            when {
                supporting.any { it.hardwareAccelerated } -> DecoderKind.HARDWARE
                supporting.isNotEmpty() -> DecoderKind.SOFTWARE
                else -> null
            }
        }
}
