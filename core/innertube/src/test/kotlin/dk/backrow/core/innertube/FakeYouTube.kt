// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.Collections

/**
 * A MockWebServer that answers the three YouTube requests the player path makes:
 * `GET /tv`, `GET /s/player/{id}/tv-player-ias.vflset/tv-player-ias.js` and `POST /youtubei/v1/player`.
 * Player responses are the recorded fixtures, chosen per client by [playerFixtures].
 */
internal class FakeYouTube : Dispatcher() {
    val server = MockWebServer()
    val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    /** clientName → fixture file; a client without an entry gets the bot-check fixture. */
    var playerFixtures: Map<String, String> = mapOf("TVHTML5" to "tv_samsung-ok-n.json", "VISIONOS" to "visionos-ok.json")
    var tvPageHtml: String = tvPage()
    var playerJs: String = "var x=1;function y(){return {signatureTimestamp:${FakeTv.STS}}};"

    fun start(): FakeYouTube = apply {
        server.dispatcher = this
        server.start()
    }

    fun shutdownServer() = server.close()

    fun http(): InnertubeHttp = InnertubeHttp(okhttp3.OkHttpClient(), server.url("/"))

    fun count(pathPrefix: String): Int = requests.count { it.url.encodedPath.startsWith(pathPrefix) }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val path = request.url.encodedPath
        return when {
            path == "/tv" -> MockResponse(body = tvPageHtml)
            path.startsWith("/s/player/") && path.endsWith("/tv-player-ias.vflset/tv-player-ias.js") -> MockResponse(body = playerJs)
            path == "/youtubei/v1/player" -> {
                val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                val clientName = body["context"]!!.jsonObject["client"]!!.jsonObject["clientName"]!!.jsonPrimitive.content
                MockResponse(body = fixture(playerFixtures[clientName] ?: "tv_samsung-login-required.json"))
            }
            else -> MockResponse(code = 404)
        }
    }
}
