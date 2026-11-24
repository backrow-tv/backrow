// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dk.backrow.core.model.Logger
import dk.backrow.core.model.StreamInfo

/** Nothing in the video can be decoded on this device. */
class NoPlayableTracksException(videoId: String) : RuntimeException("No decodable tracks for $videoId")

/** A media source ready for [ExoPlayer.setMediaSource], and what went into it. */
class PreparedMedia(val source: MediaSource, val selection: TrackSelection)

/**
 * Creates players and media sources with Backrow's policies: buffer sizes from the memory class
 * ([BufferPolicy]) and tracks this device can decode in hardware where it matters ([TrackPolicy]).
 *
 * Stream URLs and headers come from `:core:innertube` and are used as they are.
 */
@OptIn(UnstableApi::class)
class BackrowPlayerFactory(
    private val context: Context,
    private val logger: Logger,
    val decoders: DecoderSupport = MediaCodecDecoderSupport(context.applicationContext),
) {
    val bufferConfig: BufferConfig by lazy {
        val am = context.getSystemService(ActivityManager::class.java)
        val totalRam = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        BufferPolicy.forDevice(am.memoryClass, am.isLowRamDevice, totalRam).also {
            logger.info("Device tier ${it.tier} (memoryClass=${am.memoryClass} MB, lowRam=${am.isLowRamDevice}, RAM=${totalRam / (1024 * 1024)} MB)")
        }
    }

    fun createPlayer(): ExoPlayer {
        val c = bufferConfig
        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(c.minBufferMs, c.maxBufferMs, c.bufferForPlaybackMs, c.bufferForPlaybackAfterRebufferMs)
                .setTargetBufferBytes(c.targetBufferBytes)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        val bandwidthMeter =
            DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(INITIAL_BITRATE_ESTIMATE)
                .build()
        // Hardware decoders come first in the platform's codec list; fall back to the next
        // decoder if one fails to initialise (common on cheap boxes).
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
    }

    fun prepare(info: StreamInfo): PreparedMedia {
        logger.debug(
            "Decoders: " +
                info.videoFormats.sortedBy { it.height }.joinToString { f ->
                    "${f.codecs} ${f.height}p${f.fps}=" + decoders.decoderFor(mimeFor(f.codecs), f.codecs, f.width ?: 0, f.height ?: 0, f.fps ?: 30)
                },
        )
        val selection =
            TrackPolicy.select(info.videoFormats, info.audioFormats, decoders, bufferConfig.maxVideoHeight)
                ?: throw NoPlayableTracksException(info.videoId)
        logger.info(
            "Tracks for ${info.videoId}: ${selection.videoCodec} (${selection.videoDecoder}) " +
                selection.video.joinToString { "${it.height}p${it.fps ?: ""}" } + ", audio " +
                selection.audio.joinToString { it.codecs },
        )
        val xml = DashManifestBuilder.build(info.durationMs, selection.video, selection.audio)
        val manifest = DashManifestParser().parse(Uri.parse(MANIFEST_URI), xml.byteInputStream())

        val headers = info.requestHeaders
        val dataSource =
            DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"])
                .setDefaultRequestProperties(headers - "User-Agent")
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
        val mediaItem =
            MediaItem.Builder()
                .setUri(MANIFEST_URI)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setMediaId(info.videoId)
                .build()
        val source = DashMediaSource.Factory(dataSource).createMediaSource(manifest, mediaItem)
        return PreparedMedia(source, selection)
    }

    private fun mimeFor(codecs: String): String =
        when (TrackPolicy.codecFamily(codecs)) {
            "avc1" -> MimeTypes.VIDEO_H264
            "vp9" -> MimeTypes.VIDEO_VP9
            "av01" -> MimeTypes.VIDEO_AV1
            else -> MimeTypes.VIDEO_UNKNOWN
        }

    companion object {
        /** Left/Right on the remote seek by this much. */
        const val SEEK_INCREMENT_MS = 10_000L

        // The manifest is built in memory; its URLs are absolute, so this URI is never fetched.
        private const val MANIFEST_URI = "https://backrow.invalid/manifest.mpd"

        // Start near 720p/1080p H.264 bitrates instead of Media3's conservative default, so the
        // first segments are not 360p on a good connection. ABR corrects it within seconds.
        private const val INITIAL_BITRATE_ESTIMATE = 4_000_000L
    }
}
