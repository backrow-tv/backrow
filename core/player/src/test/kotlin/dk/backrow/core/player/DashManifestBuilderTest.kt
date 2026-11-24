// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import dk.backrow.core.player.TestFormats.aac
import dk.backrow.core.player.TestFormats.avc1080
import dk.backrow.core.player.TestFormats.avc720
import dk.backrow.core.player.TestFormats.opus
import dk.backrow.core.player.TestFormats.vp9_1080
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest is checked by parsing it with the JDK's XML parser (namespace-aware), so a
 * malformed or mis-escaped document fails here rather than on a TV. Media3's own parser needs
 * Android's XmlPullParser and runs in the device check instead.
 */
class DashManifestBuilderTest {
    private fun parse(xml: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(xml.byteInputStream()).documentElement

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.localName == name }

    @Test
    fun `static on-demand MPD with the precise duration`() {
        val mpd = parse(DashManifestBuilder.build(634_709, listOf(avc1080), listOf(aac)))

        assertEquals("urn:mpeg:dash:schema:mpd:2011", mpd.namespaceURI)
        assertEquals("static", mpd.getAttribute("type"))
        assertEquals("urn:mpeg:dash:profile:isoff-on-demand:2011", mpd.getAttribute("profiles"))
        assertEquals("PT634.709S", mpd.getAttribute("mediaPresentationDuration"))
    }

    @Test
    fun `one adaptation set per container and codec family, representations by bitrate`() {
        val mpd = parse(DashManifestBuilder.build(10_000, listOf(avc1080, vp9_1080, avc720), listOf(opus, aac)))
        val sets = mpd.children("Period").single().children("AdaptationSet")

        assertEquals(listOf("audio/webm", "audio/mp4", "video/mp4", "video/webm"), sets.map { it.getAttribute("mimeType") })
        assertEquals(listOf("0", "1", "2", "3"), sets.map { it.getAttribute("id") })
        val avcSet = sets.single { it.getAttribute("mimeType") == "video/mp4" }
        assertEquals(listOf("298", "299"), avcSet.children("Representation").map { it.getAttribute("id") })
    }

    @Test
    fun `video representation carries size, frame rate and SegmentBase ranges`() {
        val mpd = parse(DashManifestBuilder.build(10_000, listOf(avc1080), listOf(aac)))
        val rep = mpd.getElementsByTagNameNS("*", "Representation").let { list ->
            (0 until list.length).map { list.item(it) as Element }.single { it.getAttribute("id") == "299" }
        }

        assertEquals("avc1.64002a", rep.getAttribute("codecs"))
        assertEquals("5700000", rep.getAttribute("bandwidth"))
        assertEquals("1920", rep.getAttribute("width"))
        assertEquals("1080", rep.getAttribute("height"))
        assertEquals("60", rep.getAttribute("frameRate"))
        val segmentBase = rep.children("SegmentBase").single()
        assertEquals("740-2299", segmentBase.getAttribute("indexRange"))
        assertEquals("0-739", segmentBase.children("Initialization").single().getAttribute("range"))
    }

    @Test
    fun `audio representation carries sample rate and channel count`() {
        val mpd = parse(DashManifestBuilder.build(10_000, listOf(avc1080), listOf(aac)))
        val rep = mpd.getElementsByTagNameNS("*", "Representation").item(0) as Element

        assertEquals("140", rep.getAttribute("id"))
        assertEquals("44100", rep.getAttribute("audioSamplingRate"))
        assertEquals("2", rep.children("AudioChannelConfiguration").single().getAttribute("value"))
    }

    @Test
    fun `URLs survive XML escaping unchanged`() {
        val mpd = parse(DashManifestBuilder.build(10_000, listOf(avc1080), listOf(aac)))
        val urls = mpd.getElementsByTagNameNS("*", "BaseURL").let { l -> (0 until l.length).map { l.item(it).textContent } }

        assertEquals(listOf(aac.url, avc1080.url), urls)
    }

    @Test
    fun `durations format with three decimals`() {
        assertEquals("PT0.005S", DashManifestBuilder.duration(5))
        assertEquals("PT60.000S", DashManifestBuilder.duration(60_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `needs both video and audio`() {
        DashManifestBuilder.build(10_000, listOf(avc1080), emptyList())
    }
}
