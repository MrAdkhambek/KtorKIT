package sample.test

import io.ktor.http.HttpStatusCode
import io.ktorkit.create
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReturnTypeTests {
    @Test fun string_return() = runTest {
        val (c, _) = mockSetup(responseBody = "raw text body", contentType = "text/plain")
        assertEquals("raw text body", c.create<ReturnApi>().string())
    }
    @Test fun typed_return() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":7,"title":"hello"}""")
        val p = c.create<ReturnApi>().typed()
        assertEquals(7, p.id)
        assertEquals("hello", p.title)
    }
    @Test fun list_return() = runTest {
        val (c, _) = mockSetup(
            responseBody = """[{"id":1,"title":"a"},{"id":2,"title":"b"},{"id":3,"title":"c"}]""",
        )
        val list = c.create<ReturnApi>().list()
        assertEquals(3, list.size)
        assertEquals(2, list[1].id)
        assertEquals("c", list[2].title)
    }
    @Test fun empty_list() = runTest {
        val (c, _) = mockSetup(responseBody = "[]")
        assertEquals(0, c.create<ReturnApi>().list().size)
    }
    @Test fun map_return() = runTest {
        val (c, _) = mockSetup(responseBody = """{"a":"1","b":"two"}""")
        val m = c.create<ReturnApi>().map()
        assertEquals("1", m["a"])
        assertEquals("two", m["b"])
    }
    @Test fun nullable_null() = runTest {
        val (c, _) = mockSetup(responseBody = "null")
        assertNull(c.create<ReturnApi>().nullableTyped())
    }
    @Test fun nullable_value() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":1,"title":"x"}""")
        val r = c.create<ReturnApi>().nullableTyped()
        assertNotNull(r)
        assertEquals(1, r.id)
    }
    @Test fun list_of_maps() = runTest {
        val (c, _) = mockSetup(responseBody = """[{"a":1},{"b":2,"c":3}]""")
        val r = c.create<ReturnApi>().listOfMaps()
        assertEquals(2, r.size)
        assertEquals(1, r[0]["a"])
        assertEquals(3, r[1]["c"])
    }
    @Test fun response_string() = runTest {
        val (c, _) = mockSetup(responseBody = "raw", statusCode = HttpStatusCode.Created)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(201, r.status.value)
        assertEquals("raw", r.body)
        assertTrue(r.isSuccess)
    }
    @Test fun response_typed() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":99,"title":"r"}""")
        val r = c.create<ReturnApi>().responseTyped()
        assertEquals(99, r.body.id)
        assertEquals("r", r.body.title)
    }
    @Test fun response_list() = runTest {
        val (c, _) = mockSetup(responseBody = """[{"id":1,"title":"x"}]""")
        val r = c.create<ReturnApi>().responseList()
        assertEquals(1, r.body.size)
    }
    @Test fun response_headers() = runTest {
        val (c, _) = mockSetup(
            responseBody = "ok",
            extraHeaders = mapOf("X-Server-Token" to "abc"),
        )
        val r = c.create<ReturnApi>().responseString()
        assertEquals("abc", r.headers["X-Server-Token"])
    }
    @Test fun response_404() = runTest {
        val (c, _) = mockSetup(responseBody = "missing", statusCode = HttpStatusCode.NotFound)
        val r = c.create<ReturnApi>().responseString()
        assertEquals(404, r.status.value)
        assertFalse(r.isSuccess)
    }
    @Test fun response_500() = runTest {
        val (c, _) = mockSetup(responseBody = "boom", statusCode = HttpStatusCode.InternalServerError)
        val r = c.create<ReturnApi>().responseString()
        assertFalse(r.isSuccess)
    }
}

class UrlTests {
    @Test fun url_overrides_path() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://default.test/")
        c.create<UrlApi>().absolute("https://override.test/special/path")
        assertEquals("https://override.test/special/path", cap.url)
    }
    @Test fun url_overrides_baseurl_typed() = runTest {
        val (c, cap) = mockSetup(
            baseUrl = "https://default.test/",
            responseBody = """{"id":42,"title":"t"}""",
        )
        val p = c.create<UrlApi>().absoluteTyped("https://override.test/posts/42")
        assertEquals(42, p.id)
        assertEquals("https://override.test/posts/42", cap.url)
    }
    @Test fun normal_uses_baseurl() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://default.test/")
        c.create<UrlApi>().normal()
        assertTrue(cap.url.startsWith("https://default.test/"), cap.url)
    }
}

class DeclaredBaseUrlTests {
    @Test fun declared_baseurl_wins_empty() = runTest {
        val (c, cap) = mockSetup(baseUrl = "")
        c.create<DeclaredBaseApi>().get(7)
        assertEquals("https://api.declared.example/posts/7", cap.url)
    }
    @Test fun declared_baseurl_wins_other() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://other.test/")
        c.create<DeclaredBaseApi>().get(1)
        assertEquals("https://api.declared.example/posts/1", cap.url)
    }
    @Test fun declared_baseurl_with_list() = runTest {
        val (c, cap) = mockSetup(
            baseUrl = "",
            responseBody = """[{"id":1,"title":"a"}]""",
        )
        val list = c.create<DeclaredBaseListApi>().list()
        assertEquals(1, list.size)
        assertTrue(cap.url.startsWith("https://api.declared.example/"), cap.url)
    }
}

class CombinedTests {
    @Test fun path_plus_query() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().pathAndQuery(k = "books", q = "kotlin")
        assertTrue(cap.url.contains("/books/search"), cap.url)
        assertEquals("kotlin", cap.query("q"))
    }
}
