// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stands in for QuickJS in JVM tests. It recognises the scripts [ChallengeSolver] sends and
 * answers each like the EJS solver would, solving challenges with [solveN] and [solveSig].
 * Real solving against a real player is covered on a device by :core:jsengine's test and by
 * the live canary.
 */
internal class FakeJsRuntime(
    var solveN: (String) -> String? = { "solved-$it" },
    var solveSig: (String) -> String? = { "sig-${it.length}" },
) : JsRuntime {
    val scripts = mutableListOf<String>()
    var preprocessCount = 0
    var failPreprocessWith: String? = null
    private var preprocessed: String? = null
    private var solversLoaded = false

    override suspend fun evaluate(script: String): String {
        scripts += script
        return when {
            script.endsWith("typeof jsc") -> "function"
            script.startsWith("globalThis.__backrowPreprocessedPlayer = ") -> {
                preprocessed = script.substringAfter(" = ").substringBeforeLast("; 0")
                "0"
            }
            script == "globalThis.__backrowPreprocessedPlayer" -> checkNotNull(preprocessed)
            script.contains("\"output_preprocessed\":true") -> {
                preprocessCount++
                failPreprocessWith?.let { return """{"type":"error","error":"$it"}""" }
                preprocessed = "PREPROCESSED(${script.length})"
                "ok"
            }
            script.contains("globalThis.__backrowSolvers = r") -> {
                checkNotNull(preprocessed) { "solver functions built before the player was loaded" }
                solversLoaded = true
                "function function"
            }
            script.startsWith("(function (input) {") -> answer(script)
            else -> error("unexpected script: ${script.take(80)}")
        }
    }

    override suspend fun reset() {
        preprocessed = null
        solversLoaded = false
    }

    private fun answer(script: String): String {
        check(solversLoaded) { "solve before the solver functions were loaded" }
        val input = Json.parseToJsonElement(script.substringAfterLast("})(").removeSuffix(")")).jsonObject
        val responses =
            listOf("n" to solveN, "sig" to solveSig).map { (type, solve) ->
                val data = input[type]!!.jsonArray.associate { it.jsonPrimitive.content to solve(it.jsonPrimitive.content) }
                if (data.values.any { it == null }) {
                    """{"type":"error","error":"Failed to extract $type function"}"""
                } else {
                    """{"type":"result","data":{${data.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }}}}"""
                }
            }
        return """{"type":"result","responses":[${responses.joinToString(",")}]}"""
    }
}
