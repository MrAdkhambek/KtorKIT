package sample.test

import io.ktor.http.HttpStatusCode
import io.ktorkit.create
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReturnTypeTests {
    @Test fun `String return is the raw body`() = runTest {
        val (c, _) = mockSetup(responseBody = "raw text body", contentType = "text/plain")
        assertEquals("raw text body", c.create<ReturnApi>().string())
    }

    @Test fun `Serializable return decoded from JSON`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":7,"title":"hello"}""")
        val p = c.create<ReturnApi>().typed()
        assertEquals(7, p.id)
        assertEquals("hello", p.title)
    }

    @Test fun `List of Serializable return decoded`() = runTest {
        val (c, _) = mockSetup(
            responseBody = """[{"id":1,"title":"a"},{"id":2,"title":"b"},{"id":3,"title":"c"}]""",
        )
        val list = c.create<ReturnApi>().list()
        assertEquals(3, list.size)
        assertEquals(2, list[1].id)
        assertEquals("c", list[2].title)
    }

    @Test fun `empty List returned for empty array`() = runTest {
        val (c, _) = mockSetup(responseBody = "[]")
        assertEquals(0, c.create<ReturnApi>().list().size)
    }

    @Test fun `Map of String String decoded`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"a":"1","b":"two"}""")
        val m = c.create<ReturnApi>().map()
        assertEquals("1", m["a"])
        assertEquals("two", m["b"])
    }

    @Test fun `nullable T decoded as null when JSON null`() = runTest {
        val (c, _) = mockSetup(responseBody = "null")
        assertNull(c.create<ReturnApi>().nullableTyped())
    }

    @Test fun `nullable T decoded as value when present`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":1,"title":"x"}""")
        val r = c.create<ReturnApi>().nullableTyped()
        assertNotNull(r)
        assertEquals(1, r.id)
    }

    @Test fun `nested List of Map decoded`() = runTest {
        val (c, _) = mockSetup(responseBody = """[{"a":1},{"b":2,"c":3}]""")
        val r = c.create<ReturnApi>().listOfMaps()
        assertEquals(2, r.size)
        assertEquals(1, r[0]["a"])
        assertEquals(3, r[1]["c"])
    }

    @Test fun `Response of String exposes status and body`() = runTest {
        val (c, _) = mockSetup(responseBody = "raw", statusCode = HttpStatusCode.Created)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(201, r.status.value)
        assertEquals("raw", r.body)
        assertTrue(r.isSuccess)
    }

    @Test fun `Response of Serializable decodes body`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":99,"title":"r"}""")
        val r = c.create<ReturnApi>().responseTyped()
        assertEquals(99, r.body.id)
        assertEquals("r", r.body.title)
    }

    @Test fun `Response of List decodes`() = runTest {
        val (c, _) = mockSetup(responseBody = """[{"id":1,"title":"x"}]""")
        val r = c.create<ReturnApi>().responseList()
        assertEquals(1, r.body.size)
        assertEquals(1, r.body.first().id)
    }

    @Test fun `Response carries response headers`() = runTest {
        val (c, _) = mockSetup(
            responseBody = "ok",
            extraHeaders = mapOf("X-Server-Token" to "abc"),
        )
        val r = c.create<ReturnApi>().responseString()
        assertEquals("abc", r.headers["X-Server-Token"])
    }

    @Test fun `Response status 404 isSuccess false`() = runTest {
        val (c, _) = mockSetup(responseBody = "missing", statusCode = HttpStatusCode.NotFound)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(404, r.status.value)
        assertFalse(r.isSuccess)
    }

    @Test fun `Response status 500 isSuccess false`() = runTest {
        val (c, _) = mockSetup(responseBody = "boom", statusCode = HttpStatusCode.InternalServerError)
        val r = c.create<ReturnApi>().responseString()
        assertFalse(r.isSuccess)
        assertEquals(500, r.status.value)
    }
}
