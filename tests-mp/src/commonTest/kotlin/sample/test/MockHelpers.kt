package sample.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.toByteArray
import com.adkhambek.ktor.kit.KtorClient

class CapturedRequest {
    var url: String = ""
    var method: String = ""
    val headers = mutableMapOf<String, List<String>>()
    var body: String = ""

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.firstOrNull()

    fun query(name: String): String? {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        return q.split('&').firstNotNullOfOrNull { kv ->
            val (k, v) = kv.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (k == name) v.decodeURLPart() else null
        }
    }

    fun pathOf(): String = url.substringBefore('?')
}

fun mockSetup(
    baseUrl: String = "https://example.test/",
    responseBody: String = "{}",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
    extraHeaders: Map<String, String> = emptyMap(),
): Pair<KtorClient, CapturedRequest> {
    val capture = CapturedRequest()
    val responseHeaders = mutableMapOf<String, List<String>>()
    responseHeaders[HttpHeaders.ContentType] = listOf(contentType)
    for ((k, v) in extraHeaders) responseHeaders[k] = listOf(v)
    val http = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                capture.url = request.url.toString()
                capture.method = request.method.value
                capture.headers.clear()
                request.headers.forEach { name, values -> capture.headers[name] = values }
                request.body.contentType?.let { ct ->
                    capture.headers[HttpHeaders.ContentType] = listOf(ct.toString())
                }
                val bodyBytes = request.body.toByteArray()
                capture.body = bodyBytes.decodeToString()
                respond(
                    content = ByteReadChannel(responseBody.toByteArray()),
                    status = statusCode,
                    headers = io.ktor.http.Headers.build {
                        for ((k, vs) in responseHeaders) for (v in vs) append(k, v)
                    },
                )
            }
        }
    }
    return KtorClient(baseUrl = baseUrl, httpClient = http) to capture
}
