package com.referalli.android

import com.referalli.TrackerRequest
import com.referalli.TrackerResponse
import com.referalli.TrackerTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sends tracker requests through your app's OkHttp client (shared connection pool,
 * interceptors, certificate pinning). Requires `com.squareup.okhttp3:okhttp` 4.x or 5.x
 * on the app's classpath; this SDK only compiles against it.
 *
 * `Referalli.configure(context, "tk_…")` uses HttpURLConnection by default; pass
 * `ReferalliAndroid.configure(context, "tk_…", transport = OkHttpTrackerTransport(client))`.
 */
class OkHttpTrackerTransport(private val client: OkHttpClient) : TrackerTransport {
    override fun send(request: TrackerRequest): TrackerResponse {
        val body = request.body?.toRequestBody(JSON)
        val builder = Request.Builder().url(request.url).method(request.method, body)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            return TrackerResponse(response.code, response.body?.string() ?: "")
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
