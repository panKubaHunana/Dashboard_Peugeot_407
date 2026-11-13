package cz.kuba.dash407.data

import java.net.HttpURLConnection
import java.net.URL

/** Chyba HTTP s kódem, aby šlo rozlišit třeba neplatný API klíč (401/403). */
class HttpError(val code: Int, val body: String? = null) : Exception("HTTP $code")

/** Jednoduché síťové požadavky bez knihoven. Volat jen mimo hlavní vlákno. */
object Net {
    private const val AGENT = "Dash407/0.2 (Android; Peugeot 407 head unit)"

    fun getBytes(url: String, timeoutMs: Int = 10_000): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Accept-Language", "cs")
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code)
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun getText(url: String, timeoutMs: Int = 10_000): String = String(getBytes(url, timeoutMs), Charsets.UTF_8)

    fun postJson(url: String, body: String, timeoutMs: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) } } catch (e: Exception) { null }
                throw HttpError(code, err)
            }
            return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    fun postForm(url: String, body: String, timeoutMs: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code)
            return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}
