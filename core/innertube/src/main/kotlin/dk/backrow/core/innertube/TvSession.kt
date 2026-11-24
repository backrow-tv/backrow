// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What an anonymous session needs before it can ask for streams: visitor data (without it every
 * client gets "Sign in to confirm you're not a bot") and the id of the current player JS.
 *
 * Both come from `https://www.youtube.com/tv`, the page yt-dlp downloads for its `tv` client
 * (reference/yt-dlp/yt_dlp/extractor/youtube/_base.py, `_download_ytcfg`). Visitor data is in
 * `ytcfg.INNERTUBE_CONTEXT.client.visitorData` (`_extract_visitor_data`). It is a session
 * identifier: never log it.
 */
internal class TvSession(val visitorData: String, val playerId: String) {
    override fun toString(): String = "TvSession(playerId=$playerId)"
}

internal object TvPageParser {
    // yt-dlp _base.py extract_ytcfg: r'ytcfg\.set\s*\(\s*({.+?})\s*\)\s*;'. The page has several
    // ytcfg.set calls; we merge all of them instead of taking only the first.
    private val YTCFG = Regex("""ytcfg\.set\s*\(\s*(\{.+?\})\s*\)\s*;""")

    // yt-dlp _video.py _extract_player_info: r'/s/player/(?P<id>[a-fA-F0-9]{8,})/'
    private val PLAYER_ID = Regex("""/s/player/([a-fA-F0-9]{8,})/""")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): TvSession {
        val config = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        YTCFG.findAll(html).forEach { match ->
            runCatching { json.parseToJsonElement(match.groupValues[1]).jsonObject }.onSuccess { config.putAll(it) }
        }
        val visitorData =
            (config["INNERTUBE_CONTEXT"] as? JsonObject)
                ?.get("client")?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.contentOrNull
                ?: config["VISITOR_DATA"]?.jsonPrimitive?.contentOrNull
                ?: throw InnertubeParseException("no visitor data on the TV page")
        val playerId =
            PLAYER_ID.find(html)?.groupValues?.get(1)
                ?: throw InnertubeParseException("no player id on the TV page")
        return TvSession(visitorData, playerId)
    }
}

/** YouTube answered, but not in a shape Backrow understands. Usually means YouTube changed something. */
class InnertubeParseException(message: String) : RuntimeException(message)
