// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.ByteRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Body of `POST /youtubei/v1/player`.
 *
 * Shape from reference/yt-dlp/yt_dlp/extractor/youtube/_base.py (`_call_api`, `_extract_context`:
 * hl/timeZone/utcOffsetMinutes) and `_video.py` (`_extract_player_response`,
 * `_generate_player_context`, `_get_checkok_params`).
 */
internal fun playerRequestBody(profile: ClientProfile, videoId: String, visitorData: String, signatureTimestamp: Int?): JsonObject =
    buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                put("userAgent", profile.userAgent)
                profile.deviceMake?.let { put("deviceMake", it) }
                profile.deviceModel?.let { put("deviceModel", it) }
                profile.osName?.let { put("osName", it) }
                profile.osVersion?.let { put("osVersion", it) }
                put("hl", "en")
                put("timeZone", "UTC")
                put("utcOffsetMinutes", 0)
                put("visitorData", visitorData)
            }
        }
        put("videoId", videoId)
        putJsonObject("playbackContext") {
            putJsonObject("contentPlaybackContext") {
                put("html5Preference", "HTML5_PREF_WANTS")
                signatureTimestamp?.let { put("signatureTimestamp", it) }
            }
        }
        put("contentCheckOk", true)
        put("racyCheckOk", true)
    }

/** Headers for the player request (yt-dlp `_base.py` `generate_api_headers`). */
internal fun playerRequestHeaders(profile: ClientProfile, visitorData: String): Map<String, String> =
    mapOf(
        "X-YouTube-Client-Name" to profile.clientNameId.toString(),
        "X-YouTube-Client-Version" to profile.clientVersion,
        "Origin" to "https://www.youtube.com",
        "User-Agent" to profile.userAgent,
        "X-Goog-Visitor-Id" to visitorData,
    )

/** A parsed player response, before any challenge is solved. */
internal sealed interface PlayerResponse {
    data class Playable(val durationMs: Long, val formats: List<RawFormat>) : PlayerResponse

    data class Unplayable(val status: String, val reason: String?) : PlayerResponse {
        /** YouTube's bot check; a fresh session (new visitor data) may get past it. */
        val isBotCheck: Boolean get() = status == "LOGIN_REQUIRED" && reason?.contains("bot", ignoreCase = true) == true
    }
}

/** An adaptive format as YouTube sent it: the URL may still need [nChallenge] and [encryptedSignature] solved. */
internal data class RawFormat(
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val initRange: ByteRange,
    val indexRange: ByteRange,
    val contentLength: Long?,
    val url: HttpUrl,
    /** `s` from `signatureCipher`; the solved value goes into query parameter [signatureParameter]. */
    val encryptedSignature: String?,
    val signatureParameter: String,
) {
    val nChallenge: String? get() = url.queryParameter("n")
}

/**
 * Turns a player response into [PlayerResponse]. Field names from reference/yt-dlp
 * `_video.py` (`_extract_formats_and_subtitles`, `process_format_stream`).
 *
 * Formats Backrow cannot play as DASH `SegmentBase` (no init or index range, e.g. live or
 * OTF streams), dubbed/secondary audio tracks, and the DRC (`isDrc`) and "voice boost" (`isVb`)
 * variants of an audio itag are left out, so each itag appears once.
 */
internal object PlayerResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val MIME = Regex("""^\s*([a-z]+/[a-z0-9.+-]+)\s*;\s*codecs\s*=\s*"([^"]+)"\s*$""", RegexOption.IGNORE_CASE)

    fun parse(body: String): PlayerResponse {
        val dto =
            runCatching { json.decodeFromString<PlayerResponseDto>(body) }
                .getOrElse { throw InnertubeParseException("unreadable player response: ${it.message}") }
        val status = dto.playabilityStatus?.status ?: "UNKNOWN"
        val formats = dto.streamingData?.adaptiveFormats.orEmpty().mapNotNull(::toRawFormat)
        if (status != "OK" || formats.isEmpty()) {
            return PlayerResponse.Unplayable(status, dto.playabilityStatus?.reason ?: "no playable adaptive formats".takeIf { status == "OK" })
        }
        // The formats' approxDurationMs is precise to the millisecond (YouTube.js
        // src/utils/StreamingInfo.ts uses it too); lengthSeconds is a rounded fallback.
        val durationMs =
            dto.streamingData?.adaptiveFormats?.mapNotNull { it.approxDurationMs?.toLongOrNull() }?.maxOrNull()
                ?: dto.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000)
                ?: 0L
        return PlayerResponse.Playable(durationMs, formats)
    }

    private fun toRawFormat(f: FormatDto): RawFormat? {
        if (f.isDrc || f.isVb) return null
        if (f.audioTrack != null && !f.audioTrack.audioIsDefault) return null
        val (mime, codecs) = MIME.find(f.mimeType)?.destructured ?: return null
        val init = f.initRange?.toByteRange() ?: return null
        val index = f.indexRange?.toByteRange() ?: return null

        var encrypted: String? = null
        var sp = "signature"
        val url: HttpUrl =
            when {
                f.url != null -> f.url.toHttpUrlOrNull()
                f.signatureCipher != null -> {
                    val cipher = parseQuery(f.signatureCipher)
                    encrypted = cipher["s"]
                    sp = cipher["sp"] ?: "signature"
                    cipher["url"]?.toHttpUrlOrNull()
                }
                else -> null
            } ?: return null
        if (f.signatureCipher != null && encrypted == null) return null

        return RawFormat(
            itag = f.itag,
            mimeType = mime.lowercase(),
            codecs = codecs.trim(),
            bitrate = f.bitrate,
            width = f.width,
            height = f.height,
            fps = f.fps,
            audioSampleRate = f.audioSampleRate?.toIntOrNull(),
            audioChannels = f.audioChannels,
            initRange = init,
            indexRange = index,
            contentLength = f.contentLength?.toLongOrNull(),
            url = url,
            encryptedSignature = encrypted,
            signatureParameter = sp,
        )
    }

    private fun RangeDto.toByteRange(): ByteRange? {
        val s = start.toLongOrNull() ?: return null
        val e = end.toLongOrNull() ?: return null
        return if (s in 0..e) ByteRange(s, e) else null
    }

    /** Parses an `application/x-www-form-urlencoded` string such as `signatureCipher`. */
    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else decode(pair.substring(0, i)) to decode(pair.substring(i + 1))
        }.toMap()

    private fun decode(s: String): String = java.net.URLDecoder.decode(s, Charsets.UTF_8)

    @Serializable
    private class PlayerResponseDto(
        val playabilityStatus: PlayabilityStatusDto? = null,
        val streamingData: StreamingDataDto? = null,
        val videoDetails: VideoDetailsDto? = null,
    )

    @Serializable
    private class PlayabilityStatusDto(val status: String? = null, val reason: String? = null)

    @Serializable
    private class StreamingDataDto(val adaptiveFormats: List<FormatDto> = emptyList())

    @Serializable
    private class VideoDetailsDto(val lengthSeconds: String? = null)

    @Serializable
    private class FormatDto(
        val itag: Int,
        val mimeType: String,
        val bitrate: Int = 0,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Int? = null,
        val url: String? = null,
        val signatureCipher: String? = null,
        val initRange: RangeDto? = null,
        val indexRange: RangeDto? = null,
        val contentLength: String? = null,
        val approxDurationMs: String? = null,
        val audioSampleRate: String? = null,
        val audioChannels: Int? = null,
        val isDrc: Boolean = false,
        val isVb: Boolean = false,
        val audioTrack: AudioTrackDto? = null,
    )

    @Serializable
    private class RangeDto(val start: String, val end: String)

    @Serializable
    private class AudioTrackDto(val audioIsDefault: Boolean = false)
}
