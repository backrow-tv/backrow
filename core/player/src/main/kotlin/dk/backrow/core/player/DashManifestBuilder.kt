// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import dk.backrow.core.model.AdaptiveFormat
import java.util.Locale

/**
 * Builds a static DASH MPD from adaptive formats, so Media3's DASH support can switch between
 * them. Each format is one file with byte ranges, described with `SegmentBase` (an index range
 * plus an initialisation range), the "on-demand" DASH layout.
 *
 * Shape based on reference/YouTube.js src/utils/DashManifest.tsx (MIT): `SegmentInfo` and the
 * `MPD`/`AdaptationSet`/`Representation` attributes. Formats are grouped into one adaptation set
 * per container and codec family, because a player can only switch seamlessly within one.
 *
 * Pure Kotlin: no Android types, so it is unit-tested on the JVM.
 */
object DashManifestBuilder {
    fun build(durationMs: Long, video: List<AdaptiveFormat>, audio: List<AdaptiveFormat>): String {
        require(durationMs > 0) { "duration must be positive" }
        require(video.isNotEmpty() && audio.isNotEmpty()) { "need video and audio" }
        val sets = groups(audio) + groups(video)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" """)
            append("""type="static" minBufferTime="PT1.500S" mediaPresentationDuration="${duration(durationMs)}">""").append('\n')
            append("""  <Period id="0" start="PT0S">""").append('\n')
            sets.forEachIndexed { id, formats -> appendAdaptationSet(id, formats) }
            append("  </Period>\n")
            append("</MPD>\n")
        }
    }

    private fun groups(formats: List<AdaptiveFormat>): List<List<AdaptiveFormat>> =
        formats.groupBy { it.mimeType to TrackPolicy.codecFamily(it.codecs) }.values.map { group -> group.sortedBy { it.bitrate } }

    private fun StringBuilder.appendAdaptationSet(id: Int, formats: List<AdaptiveFormat>) {
        val first = formats.first()
        val contentType = if (first.isVideo) "video" else "audio"
        append("""    <AdaptationSet id="$id" contentType="$contentType" mimeType="${xml(first.mimeType)}" """)
        append("""startWithSAP="1" subsegmentAlignment="true">""").append('\n')
        for (f in formats) {
            append("""      <Representation id="${f.itag}" bandwidth="${f.bitrate}" codecs="${xml(f.codecs)}"""")
            if (f.isVideo) {
                f.width?.let { append(""" width="$it"""") }
                f.height?.let { append(""" height="$it"""") }
                f.fps?.let { append(""" frameRate="$it"""") }
            } else {
                f.audioSampleRate?.let { append(""" audioSamplingRate="$it"""") }
            }
            append(">\n")
            if (f.isAudio) {
                append("""        <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" """)
                append("""value="${f.audioChannels ?: 2}"/>""").append('\n')
            }
            append("        <BaseURL>${xml(f.url)}</BaseURL>\n")
            append("""        <SegmentBase indexRange="${f.indexRange}">""").append('\n')
            append("""          <Initialization range="${f.initRange}"/>""").append('\n')
            append("        </SegmentBase>\n")
            append("      </Representation>\n")
        }
        append("    </AdaptationSet>\n")
    }

    /** ISO 8601 duration with millisecond precision, e.g. `PT634.709S`. */
    internal fun duration(ms: Long): String = String.format(Locale.ROOT, "PT%d.%03dS", ms / 1000, ms % 1000)

    internal fun xml(s: String): String =
        buildString(s.length) {
            for (c in s) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
}
