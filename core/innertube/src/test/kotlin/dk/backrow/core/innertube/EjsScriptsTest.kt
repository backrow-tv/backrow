// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The bundled solver scripts must be the unmodified yt-dlp/ejs release files (ADR-0009).
 * Expected hashes are the SHA3-512 values yt-dlp pins for the same release in
 * reference/yt-dlp/yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py (`HASHES`, VERSION 0.8.0).
 * Bump them only together with the files, in a reviewed PR.
 */
class EjsScriptsTest {
    @Test
    fun `bundled solver scripts match the hashes yt-dlp pins for EJS 0_8_0`() {
        assertEquals("0.8.0", EjsScripts.VERSION)
        assertEquals(
            "ee5b307d07f55e91e4723edf5ac205cc877a474187849d757dc1322e38427b157a9d706d510c1723d3670f98e5a3f8cbcde77874a80406bd7204bc9fea30f283",
            sha3("yt.solver.core.min.js"),
        )
        assertEquals(
            "8420c259ad16e99ce004e4651ac1bcabb53b4457bf5668a97a9359be9a998a789fee8ab124ee17f91a2ea8fd84e0f2b2fc8eabcaf0b16a186ba734cf422ad053",
            sha3("yt.solver.lib.min.js"),
        )
    }

    @Test
    fun `bundled scripts keep their licence notices`() {
        assertTrue(EjsScripts.core.contains("SPDX-License-Identifier: Unlicense"))
        assertTrue(EjsScripts.lib.contains("SPDX-License-Identifier: Unlicense"))
        assertTrue("meriyah ISC notice", EjsScripts.lib.contains("Name: meriyah") && EjsScripts.lib.contains("License: ISC"))
        assertTrue("astring MIT notice", EjsScripts.lib.contains("Name: astring") && EjsScripts.lib.contains("License: MIT"))
    }

    private fun sha3(name: String): String =
        MessageDigest.getInstance("SHA3-512").digest(EjsScripts.bytes(name)).joinToString("") { "%02x".format(it) }
}
