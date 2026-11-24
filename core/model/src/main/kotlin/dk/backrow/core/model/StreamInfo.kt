// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.model

/**
 * Everything the player needs to play one video: its adaptive streams and the headers to send
 * with every stream request.
 *
 * The URLs are opaque: `:core:innertube` has already applied whatever transforms they need.
 * Other modules fetch them as they are, with exactly [requestHeaders] (CLAUDE.md, YouTube
 * network boundary).
 */
data class StreamInfo(
    val videoId: String,
    val durationMs: Long,
    val videoFormats: List<AdaptiveFormat>,
    val audioFormats: List<AdaptiveFormat>,
    val requestHeaders: Map<String, String>,
    /** Which client profile produced these streams; for logs and bug reports only. */
    val source: String,
)

/** One adaptive (video-only or audio-only) stream, served as a single file with byte ranges. */
data class AdaptiveFormat(
    val itag: Int,
    /** Container MIME type without parameters, e.g. `video/mp4` or `audio/webm`. */
    val mimeType: String,
    /** RFC 6381 codec string, e.g. `avc1.640028`, `vp9`, `av01.0.08M.08`, `mp4a.40.2`, `opus`. */
    val codecs: String,
    val bitrate: Int,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    /** Byte range of the initialisation segment. */
    val initRange: ByteRange,
    /** Byte range of the segment index (`sidx` in MP4, `Cues` in WebM). */
    val indexRange: ByteRange,
    val contentLength: Long? = null,
    val url: String,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

/** An inclusive byte range, as used by HTTP `Range` headers and DASH `SegmentBase`. */
data class ByteRange(val start: Long, val end: Long) {
    init {
        require(start in 0..end) { "invalid byte range $start-$end" }
    }

    override fun toString(): String = "$start-$end"
}
