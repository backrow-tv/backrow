// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.fixtures

import java.io.File

/**
 * Finds secrets in recorded test fixtures (CLAUDE.md rules 5 and 6).
 *
 * Scans every file under any `src/test/resources` directory in the repository and reports
 * lines that look like visitor data, cookies, authorization headers or tokens. A fixture
 * that trips this must be re-sanitised (replace the value with a fake one such as
 * "REDACTED"); never weaken a pattern to make a fixture pass.
 */
object FixtureScanner {
    data class Finding(val file: String, val line: Int, val rule: String)

    /** Each rule is a name shown in the failure message and a case-insensitive pattern. */
    val rules: Map<String, Regex> =
        mapOf(
            "visitor data" to """visitor_?data|VISITOR_INFO1_LIVE|VISITOR_PRIVACY_METADATA""",
            "cookie header" to """\b(set-)?cookie\b"?\s*[:=]""",
            "authorization header" to """\bauthorization\b"?\s*[:=]""",
            "bearer token" to """\bbearer\s+[a-z0-9._~+/-]{8,}""",
            "Google session cookie" to """\b(__Secure-[0-9]?P?SID[A-Z]*|SAPISID|APISID|HSID|SSID|LOGIN_INFO)\b""",
            "OAuth token field" to """\b(access|refresh|id)_token\b""",
            "Google OAuth access token" to """\bya29\.[a-z0-9_-]{10,}""",
            "Google OAuth refresh token" to """\b1//0[a-z0-9_-]{20,}""",
            "Google API key" to """\bAIza[0-9a-z_-]{35}\b""",
        ).mapValues { (_, pattern) -> Regex(pattern, RegexOption.IGNORE_CASE) }

    private val skippedDirectories = setOf(".git", ".gradle", "build", "reference", ".idea", ".kotlin")

    fun scan(root: File): List<Finding> =
        fixtureFiles(root).flatMap { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            file.readText(Charsets.UTF_8).lineSequence().flatMapIndexed { index, line ->
                rules.filterValues { it.containsMatchIn(line) }.keys.map { Finding(relative, index + 1, it) }
            }.toList()
        }

    private fun fixtureFiles(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it == root || it.name !in skippedDirectories }
            .filter { it.isFile && "/src/test/resources/" in "/" + it.relativeTo(root).invariantSeparatorsPath }
            .sortedBy { it.path }
            .toList()
}
