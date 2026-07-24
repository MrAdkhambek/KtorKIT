package sample.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktorkit.KtorClient
import io.ktorkit.create
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A client whose engine always fails, for exercising transport-level errors. */
private fun failingClient(error: Throwable): KtorClient {
    val http = HttpClient(MockEngine) {
        engine { addHandler { throw error } }
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
    @Test fun `malformed JSON for typed return throws SerializationException`() = runTest {
        val (c, _) = mockSetup(responseBody = "this is not json")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun `JSON of the wrong shape throws SerializationException`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"unexpected":true}""")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun `empty body for typed return throws SerializationException`() = runTest {
        val (c, _) = mockSetup(responseBody = "")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun `JSON object where a list is expected throws`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":1,"title":"a"}""")
        assertFailsWith<SerializationException> { c.create<ReturnApi>().list() }
    }

    @Test fun `unknown keys are ignored not fatal`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":1,"title":"a","extra":"ignored"}""")
        val p = c.create<ReturnApi>().typed()
        assertEquals(1, p.id)
    }

    @Test fun `malformed line in a stream throws on collection`() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}\nnot-json")
        assertFailsWith<SerializationException> { c.create<StreamApi>().posts().toList() }
    }
}

class EmptyBodyTests {
    @Test fun `empty body for String return is empty string`() = runTest {
        val (c, _) = mockSetup(responseBody = "", contentType = "text/plain")
        assertEquals("", c.create<ReturnApi>().string())
    }

    @Test fun `empty body for Response of String is empty with status`() = runTest {
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

    @Test fun `whitespace-only stream yields no elements`() = runTest {
        val (c, _) = mockSetup(responseBody = "   \n\n  \n")
        assertEquals(0, c.create<StreamApi>().posts().toList().size)
    }
}

class TransportErrorTests {
    @Test fun `engine failure propagates to the caller`() = runTest {
        val c = failingClient(IOException("connection reset"))
        val thrown = assertFailsWith<IOException> { c.create<VerbsApi>().get() }
        assertEquals("connection reset", thrown.message)
    }

    @Test fun `engine failure propagates for typed returns`() = runTest {
        val c = failingClient(IOException("timeout"))
        assertFailsWith<IOException> { c.create<ReturnApi>().typed() }
    }

    @Test fun `engine failure propagates through a Flow`() = runTest {
        val c = failingClient(IOException("stream broke"))
        assertFailsWith<IOException> { c.create<StreamApi>().posts().toList() }
    }

    @Test fun `error status with typed return still attempts to decode`() = runTest {
        // Documents current behaviour: non-2xx bodies are decoded like any other.
        // Use Response<T> when you need to branch on the status code instead.
        val (c, _) = mockSetup(
            responseBody = """{"error":"not found"}""",
            statusCode = HttpStatusCode.NotFound,
        )
        assertFailsWith<SerializationException> { c.create<ReturnApi>().typed() }
    }

    @Test fun `error status with Response return does not throw`() = runTest {
        val (c, _) = mockSetup(responseBody = "nope", statusCode = HttpStatusCode.NotFound)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(404, r.status.value)
        assertEquals("nope", r.body)
    }
}

class ConcurrencyTests {
    @Test fun `concurrent calls on one instance each get their own response`() = runTest {
        val api = echoingClient().create<QueryApi>()
        val results = coroutineScope {
            (1..50).map { n -> async { api.single("value-$n") } }.awaitAll()
        }
        assertEquals(50, results.size)
        assertEquals((1..50).map { "value-$it" }, results)
    }

    @Test fun `concurrent calls across separate instances are independent`() = runTest {
        val client = echoingClient()
        val results = coroutineScope {
            (1..20).map { n -> async { client.create<QueryApi>().single("v$n") } }.awaitAll()
        }
        assertEquals((1..20).map { "v$it" }, results)
    }

    @Test fun `a created api instance is reusable across sequential calls`() = runTest {
        val api = echoingClient().create<QueryApi>()
        assertEquals("first", api.single("first"))
        assertEquals("second", api.single("second"))
        assertEquals("third", api.single("third"))
    }
}
