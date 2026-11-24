// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The TV page carries visitor data, which the fixture scanner (rightly) forbids in
 * src/test/resources. These tests therefore build a page of the recorded shape in code, with
 * fake values: ytcfg.set calls as on https://www.youtube.com/tv on 2026-11-24.
 */
class TvPageParserTest {
    @Test
    fun `reads visitor data from INNERTUBE_CONTEXT and the player id from the player URL`() {
        val session = TvPageParser.parse(tvPage())

        assertEquals(FakeTv.VISITOR, session.visitorData)
        assertEquals(FakeTv.PLAYER_ID, session.playerId)
    }

    @Test
    fun `merges several ytcfg set calls and skips one that is not JSON`() {
        val page =
            """
            <script>ytcfg.set({"HL":"en"});</script>
            <script>ytcfg.set({not json});</script>
            <script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"TVHTML5","visitorData":"${FakeTv.VISITOR}"}}}) ;</script>
            <script src="/s/player/${FakeTv.PLAYER_ID}/tv-player-ias.vflset/tv-player-ias.js"></script>
            """.trimIndent()

        assertEquals(FakeTv.VISITOR, TvPageParser.parse(page).visitorData)
    }

    @Test(expected = InnertubeParseException::class)
    fun `fails clearly when the page has no visitor data`() {
        TvPageParser.parse("""<script>ytcfg.set({"HL":"en"});</script> /s/player/${FakeTv.PLAYER_ID}/x.js""")
    }

    @Test(expected = InnertubeParseException::class)
    fun `fails clearly when the page names no player`() {
        TvPageParser.parse(tvPage().replace("/s/player/", "/elsewhere/"))
    }

    @Test
    fun `never prints visitor data`() {
        assertFalse(TvPageParser.parse(tvPage()).toString().contains(FakeTv.VISITOR))
    }
}

/** Fake TV-page values shared by the tests. */
internal object FakeTv {
    const val VISITOR = "FAKE_VISITOR_0123456789"
    const val PLAYER_ID = "0a1b2c3d"
    const val STS = 20775
}

internal fun tvPage(visitor: String = FakeTv.VISITOR, playerId: String = FakeTv.PLAYER_ID): String =
    """
    <!DOCTYPE html><html><head>
    <script nonce="x">ytcfg.set({"CLIENT_CANARY_STATE":"none","HL":"en","INNERTUBE_CLIENT_NAME":"TVHTML5","INNERTUBE_CLIENT_VERSION":"7.20261122.19.00","INNERTUBE_CONTEXT":{"client":{"hl":"en","gl":"DK","clientName":"TVHTML5","clientVersion":"7.20261122.19.00","visitorData":"$visitor","platform":"TV"}},"LOGGED_IN":false});</script>
    <script src="/s/player/$playerId/tv-player-ias.vflset/tv-player-ias.js"></script>
    </head><body></body></html>
    """.trimIndent()
