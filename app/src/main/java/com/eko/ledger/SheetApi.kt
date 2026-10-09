package com.eko.ledger

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to the Apps Script web app. Apps Script answers a POST with a 302 to a
 * googleusercontent.com URL that holds the response, so redirects are followed by hand
 * (POST first, then GET the Location).
 */
object SheetApi {

    fun post(url: String, body: JSONObject): JSONObject =
        request(url, "POST", body.toString())

    fun ping(url: String, token: String): JSONObject =
        request("$url?action=ping&token=${URLEncoder.encode(token, "UTF-8")}", "GET", null)

    private fun request(start: String, firstMethod: String, payload: String?): JSONObject {
        var url = start
        var method = firstMethod
        repeat(6) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 40_000
                requestMethod = method
                if (method == "POST" && payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                    outputStream.use { it.write(payload.toByteArray()) }
                }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    url = conn.getHeaderField("Location") ?: error("Redirect without Location")
                    method = "GET"
                    return@repeat
                }
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) error("HTTP $code")
                return try { JSONObject(text) } catch (_: Exception) {
                    error(if (text.contains("<html", true)) "Got a web page, not JSON — check the URL ends in /exec and access is 'Anyone'" else "Bad response")
                }
            } finally { conn.disconnect() }
        }
        error("Too many redirects")
    }
}
