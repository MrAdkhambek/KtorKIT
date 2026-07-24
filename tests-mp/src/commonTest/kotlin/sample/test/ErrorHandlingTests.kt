package sample.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import com.adkhambek.ktor.kit.KtorClient
import com.adkhambek.ktor.kit.create
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class TransportFailure(message: String) : RuntimeException(message)

/** A client whose engine always fails, for exercising transport-level errors. */
private fun failingClient(): KtorClient {
    val http = HttpClient(MockEngine) {
        engine { addHandler { throw TransportFailure("connection reset") } }
    }
    return KtorClient(baseUrl = "https://example.test/", httpClient = http)
}

/** A client that echoes the `q` query parameter back as the response body. */
private fun echoingClient(): KtorClient {
    val http = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = ByteReadChannel(request.url.parameters["q"].orEmpty()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            }
        }
    }
    return KtorClient(baseUrl = "https://example.test/", httpClient = http)
}

class MalformedResponseTests {
    @Test fun malformed_json_throws() = runTest {
        val (c, _) = mockSetup(responseBody = "this is not json")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun wrong_shape_throws() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"unexpected\":true}")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun empty_body_typed_throws() = runTest {
        val (c, _) = mockSetup(responseBody = "")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun object_where_list_expected_throws() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().list() }
    }

    @Test fun unknown_keys_ignored() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\",\"extra\":\"ignored\"}")
        assertEquals(1, c.create<ReturnApi>().typed().id)
    }

    @Test fun malformed_stream_line_throws() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}\nnot-json")
        assertFailsWith<SerializationException> { c.create<StreamApi>().posts().toList() }
    }
}

class EmptyBodyTests {
    @Test fun empty_string_return() = runTest {
        val (c, _) = mockSetup(responseBody = "", contentType = "text/plain")
        assertEquals("", c.create<ReturnApi>().string())
    }

    @Test fun empty_response_string_with_status() = runTest {
        val (c, _) = mockSetup(
            responseBody = "",
            statusCode = HttpStatusCode.NoContent,
            contentType = "text/plain",
        )
        val r = c.create<ReturnApi>().responseString()
        assertEquals(204, r.status.value)
        assertEquals("", r.body)
        assertTrue(r.isSuccess)
    }

    @Test fun whitespace_only_stream() = runTest {
        val (c, _) = mockSetup(responseBody = "   \n\n  \n")
        assertEquals(0, c.create<StreamApi>().posts().toList().size)
    }
}

class TransportErrorTests {
    @Test fun engine_failure_propagates() = runTest {
        val thrown = assertFailsWith<TransportFailure> { failingClient().create<VerbsApi>().get() }
        assertEquals("connection reset", thrown.message)
    }

    @Test fun engine_failure_propagates_typed() = runTest {
        assertFailsWith<TransportFailure> { failingClient().create<ReturnApi>().typed() }
    }

    @Test fun engine_failure_propagates_flow() = runTest {
        assertFailsWith<TransportFailure> { failingClient().create<StreamApi>().posts().toList() }
    }

    @Test fun error_status_typed_still_decodes() = runTest {
        // Documents current behaviour: non-2xx bodies are decoded like any other.
        // Use Response<T> when you need to branch on the status code instead.
        val (c, _) = mockSetup(
            responseBody = "{\"error\":\"not found\"}",
            statusCode = HttpStatusCode.NotFound,
        )
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun error_status_response_does_not_throw() = runTest {
        val (c, _) = mockSetup(responseBody = "nope", statusCode = HttpStatusCode.NotFound)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(404, r.status.value)
        assertEquals("nope", r.body)
    }
}

class ConcurrencyTests {
    @Test fun concurrent_calls_one_instance() = runTest {
        val api = echoingClient().create<QueryApi>()
        val results = coroutineScope {
            (1..50).map { n -> async { api.single("value-$n") } }.awaitAll()
        }
        assertEquals((1..50).map { "value-$it" }, results)
    }

    @Test fun concurrent_calls_separate_instances() = runTest {
        val client = echoingClient()
        val results = coroutineScope {
            (1..20).map { n -> async { client.create<QueryApi>().single("v$n") } }.awaitAll()
        }
        assertEquals((1..20).map { "v$it" }, results)
    }

    @Test fun instance_reusable_sequentially() = runTest {
        val api = echoingClient().create<QueryApi>()
        assertEquals("first", api.single("first"))
        assertEquals("second", api.single("second"))
        assertEquals("third", api.single("third"))
    }
}
