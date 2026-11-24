// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A request to YouTube answered with a non-2xx status. */
class InnertubeHttpException(val code: Int, val path: String) : IOException("HTTP $code for $path")

/**
 * The only place that sends requests to YouTube's web hosts. [baseUrl] is
 * `https://www.youtube.com/` in the app and a MockWebServer URL in tests.
 */
internal class InnertubeHttp(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) {
    suspend fun get(path: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url(path)).headers(headers).get().build(), path)

    suspend fun postJson(path: String, query: Map<String, String>, json: String, headers: Map<String, String>): String {
        val url = url(path).newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val body = json.toRequestBody(JSON)
        return execute(Request.Builder().url(url).headers(headers).post(body).build(), path)
    }

    private fun url(path: String): HttpUrl = checkNotNull(baseUrl.resolve(path)) { "bad path $path" }

    private fun Request.Builder.headers(headers: Map<String, String>): Request.Builder =
        apply { headers.forEach { (name, value) -> header(name, value) } }

    private suspend fun execute(request: Request, path: String): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result =
                            response.use {
                                if (it.isSuccessful) {
                                    runCatching { it.body.string() }
                                } else {
                                    Result.failure(InnertubeHttpException(it.code, path))
                                }
                            }
                        result.fold(continuation::resume, continuation::resumeWithException)
                    }
                },
            )
        }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://www.youtube.com/".toHttpUrl()
        private val JSON = "application/json".toMediaType()
    }
}
