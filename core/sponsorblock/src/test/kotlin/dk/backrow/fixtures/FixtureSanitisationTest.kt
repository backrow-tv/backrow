// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FixtureSanitisationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `no fixture in the repository contains secrets`() {
        val root = File(checkNotNull(System.getProperty("backrow.rootDir")) { "backrow.rootDir not set by Gradle" })

        val findings = FixtureScanner.scan(root)

        assertTrue(
            "Fixtures must be sanitised (CLAUDE.md rule 6). Replace these values with fakes:\n" +
                findings.joinToString("\n") { "  ${it.file}:${it.line}  ${it.rule}" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `scanner catches one planted example of every rule`() {
        // These values are fake; they only have the shape of the real thing.
        val planted =
            mapOf(
                "visitor data" to """"visitorData": "Cgt4eHh4eHh4eHh4eA%3D%3D"""",
                "cookie header" to "Cookie: PREF=f6=40000000",
                "authorization header" to """"Authorization": "x"""",
                "bearer token" to "Bearer abcdefgh12345678",
                "Google session cookie" to "SAPISID=fake",
                "OAuth token field" to """{"refresh_token": "x"}""",
                "Google OAuth access token" to "ya29.a0fakefakefakefake",
                "Google OAuth refresh token" to "1//0fakefakefakefakefakefake",
                "Google API key" to "AIza" + "x".repeat(35),
            )
        assertEquals("every rule needs a planted example", FixtureScanner.rules.keys, planted.keys)

        val resources = File(temporaryFolder.root, "core/example/src/test/resources").apply { mkdirs() }
        File(resources, "planted.txt").writeText(planted.values.joinToString("\n"))

        val found = FixtureScanner.scan(temporaryFolder.root).map { it.rule }.toSet()

        assertEquals(planted.keys, found)
    }

    @Test
    fun `scanner passes a sanitised fixture and ignores files outside test resources`() {
        val resources = File(temporaryFolder.root, "core/example/src/test/resources").apply { mkdirs() }
        File(resources, "clean.json").writeText("""{"videoId": "REDACTED", "title": "A video"}""")
        val elsewhere = File(temporaryFolder.root, "core/example/src/main/resources").apply { mkdirs() }
        File(elsewhere, "not-a-fixture.txt").writeText("SAPISID=fake")

        assertEquals(emptyList<FixtureScanner.Finding>(), FixtureScanner.scan(temporaryFolder.root))
    }
}
