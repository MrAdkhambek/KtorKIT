package com.adkhambek.ktor.kit

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

class RequestBuilder internal constructor(
    private val client: KtorClient,
    private val httpMethod: String,
    private val pathTemplate: String,
) {
    private class MultipartPart(val name: String, val fileName: String, val value: Any?)

    private val pathBindings = mutableMapOf<String, Any?>()
    private val queryBindings = mutableMapOf<String, Any?>()
    private val headerBindings = mutableListOf<Pair<String, String>>()
    private val formFields = mutableListOf<Pair<String, String>>()
    private val multipartParts = mutableListOf<MultipartPart>()
    private var requestBody: Any? = null
    private var isJsonBody: Boolean = false
    private var isFormEncoded: Boolean = false
    private var isMultipart: Boolean = false
    private var baseUrlOverride: String? = null
    private var absoluteUrl: String? = null

    fun path(name: String, value: Any?) = apply { pathBindings[name] = value }
    fun query(name: String, value: Any?) = apply { queryBindings[name] = value }
    fun header(name: String, value: Any?) = apply {
        if (value != null) headerBindings += name to value.toString()
    }
    fun queryMap(map: Map<String, Any?>?) = apply {
        map?.forEach { (k, v) -> queryBindings[k] = v }
    }
    fun headerMap(map: Map<String, Any?>?) = apply {
        map?.forEach { (k, v) -> header(k, v) }
    }
    fun body(value: Any?) = apply { requestBody = value }
    fun <T> bodyJson(value: T, serializer: SerializationStrategy<T>) = apply {
        requestBody = ktorkitJson.encodeToString(serializer, value)
        isJsonBody = true
    }
    fun formEncoded() = apply { isFormEncoded = true }
    fun field(name: String, value: Any?) = apply {
        if (value != null) formFields += name to value.toString()
    }
    fun fieldMap(map: Map<String, Any?>?) = apply {
        map?.forEach { (k, v) -> field(k, v) }
    }
    fun multipart() = apply { isMultipart = true }
    fun part(name: String, fileName: String, value: Any?) = apply {
        if (value != null) multipartParts += MultipartPart(name, fileName, value)
    }
    fun useBaseUrl(value: String) = apply { if (value.isNotEmpty()) baseUrlOverride = value }
    fun useUrl(value: String) = apply { absoluteUrl = value }

    suspend fun <T> executeWithDeserializer(deserializer: DeserializationStrategy<T>): T {
        val text = executeAsString()
        return ktorkitJson.decodeFromString(deserializer, text)
    }

    suspend fun executeAsString(): String = doRequest().bodyAsText()

    /**
     * Performs the request and discards the body. Backs functions declared to return
     * `Unit`, e.g. a DELETE whose 204 response carries nothing worth decoding.
     */
    suspend fun executeIgnoringBody() {
        doRequest()
    }

    suspend fun executeAsResponseString(): Response<String> {
        val raw = doRequest()
        return Response(raw.status, raw.headers, raw.bodyAsText())
    }

    suspend fun <T> executeAsResponseWithDeserializer(
        deserializer: DeserializationStrategy<T>,
    ): Response<T> {
        val raw = doRequest()
        val text = raw.bodyAsText()
        return Response(raw.status, raw.headers, ktorkitJson.decodeFromString(deserializer, text))
    }

    /**
     * Streams the response body one line at a time, decoding each non-blank line as [T].
     * The connection stays open for the lifetime of the collection, so nothing is
     * buffered into memory up front.
     */
    fun <T> executeAsFlow(deserializer: DeserializationStrategy<T>): Flow<T> =
        executeAsFlowOfString()
            .filter { it.isNotBlank() }
            .map { ktorkitJson.decodeFromString(deserializer, it) }

    /**
     * Streams the response body as raw lines, blank lines included. This is the single
     * reader both streaming entry points are built on.
     *
     * `channelFlow` rather than `flow`: Ktor dispatches [execute] on its own coroutine on
     * some targets (notably JS), which would violate `flow`'s context-preservation rule.
     */
    fun executeAsFlowOfString(): Flow<String> = channelFlow {
        client.httpClient.prepareRequest { configureRequest() }.execute { response ->
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                send(line)
            }
        }
    }

    private suspend fun doRequest(): HttpResponse =
        client.httpClient.request { configureRequest() }

    private fun HttpRequestBuilder.configureRequest() {
        var path = pathTemplate
        for ((k, v) in pathBindings) path = path.replace("{$k}", v.toString().encodeURLPathPart())
        val finalUrl = absoluteUrl ?: run {
            val base = (baseUrlOverride ?: client.baseUrl).trimEnd('/')
            "$base/${path.trimStart('/')}"
        }
        method = HttpMethod.parse(httpMethod)
        url(finalUrl)
        for ((k, v) in queryBindings) if (v != null) parameter(k, v)
        for ((k, v) in headerBindings) header(k, v)
        when {
            isMultipart -> setBody(
                MultiPartFormDataContent(formData { multipartParts.forEach { appendPart(it) } })
            )
            isFormEncoded -> setBody(
                FormDataContent(Parameters.build { formFields.forEach { (k, v) -> append(k, v) } })
            )
            else -> requestBody?.let { body ->
                if (isJsonBody) contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private fun FormBuilder.appendPart(part: MultipartPart) {
        when (val value = part.value) {
            null -> Unit
            is ByteArray -> append(
                key = part.name,
                value = value,
                headers = Headers.build {
                    if (part.fileName.isNotEmpty()) {
                        append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName}\"")
                    }
                },
            )
            else -> append(part.name, value.toString())
        }
    }
}

fun KtorClient.beginRequest(method: String, pathTemplate: String): RequestBuilder =
    RequestBuilder(this, method, pathTemplate)

@PublishedApi
internal val ktorkitJson: Json = Json { ignoreUnknownKeys = true }
