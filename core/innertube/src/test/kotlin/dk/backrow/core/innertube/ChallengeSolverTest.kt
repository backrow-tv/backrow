// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import dk.backrow.core.model.NoOpLogger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ChallengeSolverTest {
    private lateinit var youtube: FakeYouTube

    @Before
    fun setUp() {
        youtube = FakeYouTube().start()
    }

    @After
    fun tearDown() = youtube.shutdownServer()

    @Test
    fun `downloads the TV player build, reads its signature timestamp and preprocesses it once`() =
        runTest {
            val js = FakeJsRuntime()
            val solver = ChallengeSolver(youtube.http(), js, InMemoryPlayerJsCache(), NoOpLogger)

            assertEquals(FakeTv.STS, solver.preparePlayer(FakeTv.PLAYER_ID))
            assertEquals(FakeTv.STS, solver.preparePlayer(FakeTv.PLAYER_ID))

            assertEquals(1, youtube.count("/s/player/${FakeTv.PLAYER_ID}/tv-player-ias.vflset/tv-player-ias.js"))
            assertEquals(1, js.preprocessCount)
            assertTrue("prelude loads lib then core", js.scripts.first().startsWith(EjsScripts.lib))
            assertTrue("player JS is passed to the solver", js.scripts.any { "signatureTimestamp:${FakeTv.STS}" in it })
        }

    @Test
    fun `a new process reuses the cached preprocessed player without downloading`() =
        runTest {
            val cache = InMemoryPlayerJsCache()
            ChallengeSolver(youtube.http(), FakeJsRuntime(), cache, NoOpLogger).preparePlayer(FakeTv.PLAYER_ID)

            val js = FakeJsRuntime()
            val sts = ChallengeSolver(youtube.http(), js, cache, NoOpLogger).preparePlayer(FakeTv.PLAYER_ID)
            val solutions = ChallengeSolver(youtube.http(), js, cache, NoOpLogger).run {
                preparePlayer(FakeTv.PLAYER_ID)
                solve(setOf("abc"), emptySet())
            }

            assertEquals(FakeTv.STS, sts)
            assertEquals(1, youtube.count("/s/player/"))
            assertEquals(0, js.preprocessCount)
            assertEquals("solved-abc", solutions.n["abc"])
        }

    @Test
    fun `solves n and sig challenges in one call`() =
        runTest {
            val js = FakeJsRuntime()
            val solver = ChallengeSolver(youtube.http(), js, InMemoryPlayerJsCache(), NoOpLogger)
            solver.preparePlayer(FakeTv.PLAYER_ID)

            val solutions = solver.solve(setOf("n1", "n2"), setOf("SSSS"))

            assertEquals(mapOf("n1" to "solved-n1", "n2" to "solved-n2"), solutions.n)
            assertEquals(mapOf("SSSS" to "sig-4"), solutions.sig)
            assertEquals(1, js.scripts.count { it.startsWith("(function (input) {") })
        }

    @Test
    fun `solver error output becomes a ChallengeSolverException`() =
        runTest {
            val js = FakeJsRuntime(solveN = { null })
            val solver = ChallengeSolver(youtube.http(), js, InMemoryPlayerJsCache(), NoOpLogger)
            solver.preparePlayer(FakeTv.PLAYER_ID)

            try {
                solver.solve(setOf("n1"), emptySet())
                fail("expected ChallengeSolverException")
            } catch (e: ChallengeSolverException) {
                assertTrue(e.message!!.contains("Failed to extract n function"))
            }
        }

    @Test
    fun `preprocessing error is reported with the solver's message`() =
        runTest {
            val js = FakeJsRuntime().apply { failPreprocessWith = "no n function found" }
            val solver = ChallengeSolver(youtube.http(), js, InMemoryPlayerJsCache(), NoOpLogger)

            try {
                solver.preparePlayer(FakeTv.PLAYER_ID)
                fail("expected ChallengeSolverException")
            } catch (e: ChallengeSolverException) {
                assertTrue(e.message!!.contains("no n function found"))
            }
        }

    @Test(expected = InnertubeParseException::class)
    fun `player without a signature timestamp is a parse error`() =
        runTest {
            youtube.playerJs = "var x=1;"
            ChallengeSolver(youtube.http(), FakeJsRuntime(), InMemoryPlayerJsCache(), NoOpLogger).preparePlayer(FakeTv.PLAYER_ID)
        }

    @Test
    fun `file cache round-trips and keeps only the newest players`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val cache = FilePlayerJsCache(dir, keep = 2)
            cache.put("aaaaaaaa", CachedPlayer(11111, "A"))
            File(dir, "aaaaaaaa.sts").setLastModified(1_000)
            cache.put("bbbbbbbb", CachedPlayer(22222, "B"))
            File(dir, "bbbbbbbb.sts").setLastModified(2_000)
            cache.put("cccccccc", CachedPlayer(33333, "C"))

            assertEquals(null, cache.get("aaaaaaaa"))
            assertEquals(22222, cache.get("bbbbbbbb")?.signatureTimestamp)
            assertEquals("C", cache.get("cccccccc")?.preprocessedPlayer)
        } finally {
            dir.deleteRecursively()
        }
    }
}

private typealias File = java.io.File
