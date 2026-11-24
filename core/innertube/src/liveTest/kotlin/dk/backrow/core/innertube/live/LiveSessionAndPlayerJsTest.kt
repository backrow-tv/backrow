// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube.live

import dk.backrow.core.innertube.ClientProfiles
import dk.backrow.core.innertube.TvPageParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Canary for the steps before any player request (ADR-0007, ADR-0009): the anonymous /tv session
 * and the TV build of the player JS, solved with the bundled EJS solver.
 */
class LiveSessionAndPlayerJsTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(180)

    @Before fun live() = LiveYouTube.assumeEnabled()

    @Test
    fun `the tv page hands out visitor data and a player id`() =
        runBlocking {
            val html = LiveYouTube.http.get("tv", mapOf("User-Agent" to ClientProfiles.TV_PAGE_USER_AGENT))
            // parse() throws if either is missing. The visitor data is never printed.
            val session = TvPageParser.parse(html)
            assertTrue("visitor data is empty", session.visitorData.isNotBlank())
            assertTrue("unexpected player id format", Regex("[a-fA-F0-9]{8,}").matches(session.playerId))
            RedactingLogger.info("Current player ${session.playerId}")
        }

    @Test
    fun `the TV player JS has a signature timestamp and the solver answers n and sig`() =
        runBlocking {
            val html = LiveYouTube.http.get("tv", mapOf("User-Agent" to ClientProfiles.TV_PAGE_USER_AGENT))
            val playerId = TvPageParser.parse(html).playerId

            // Downloads s/player/<id>/tv-player-ias.vflset/tv-player-ias.js (ChallengeSolver.PLAYER_PATH),
            // reads the signature timestamp and preprocesses the player with EJS.
            val sts = LiveYouTube.solver.preparePlayer(playerId)
            assertTrue("signature timestamp $sts is not five digits", sts in 10_000..99_999)

            // Made-up challenges in the shape YouTube uses. The point is that both functions were
            // extracted from this player and run: a missing function comes back as a solver error.
            val solutions = LiveYouTube.solver.solve(setOf(N_CHALLENGE), setOf(SIG_CHALLENGE))
            val n = solutions.n[N_CHALLENGE]
            val sig = solutions.sig[SIG_CHALLENGE]
            assertTrue("n solver gave no answer", !n.isNullOrEmpty())
            assertNotEquals("n solver returned its input", N_CHALLENGE, n)
            assertTrue("sig solver gave no answer", !sig.isNullOrEmpty())
            assertNotEquals("sig solver returned its input", SIG_CHALLENGE, sig)
            // Signature transforms reorder and drop characters; they never lengthen the input.
            assertTrue("sig answer is longer than the challenge", sig!!.length <= SIG_CHALLENGE.length)
        }

    private companion object {
        const val N_CHALLENGE = "Zq3kL9vXa0bT1wE5"
        val SIG_CHALLENGE = ('A'..'Z').joinToString("") + ('a'..'z').joinToString("") + ('0'..'9').joinToString("") +
            "-_" + ('A'..'Z').joinToString("") + ('a'..'z').joinToString("") + "=AbCdEfGhIjKlMnOpQrStUvW"
    }
}
