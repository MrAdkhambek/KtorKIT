package sample.test

import io.ktorkit.create
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerbTests {
    @Test fun `GET maps to HTTP GET`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().get()
        assertEquals("GET", cap.method)
    }
    @Test fun `POST maps to HTTP POST`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().post()
        assertEquals("POST", cap.method)
    }
    @Test fun `PUT maps to HTTP PUT`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().put()
        assertEquals("PUT", cap.method)
    }
    @Test fun `DELETE maps to HTTP DELETE`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().delete()
        assertEquals("DELETE", cap.method)
    }
    @Test fun `PATCH maps to HTTP PATCH`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().patch()
        assertEquals("PATCH", cap.method)
    }
    @Test fun `HEAD maps to HTTP HEAD`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().head()
        assertEquals("HEAD", cap.method)
    }
    @Test fun `OPTIONS maps to HTTP OPTIONS`() = runTest {
        val (c, cap) = mockSetup()
        c.create<VerbsApi>().options()
        assertEquals("OPTIONS", cap.method)
    }
    @Test fun `URL is base + path`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://api.example/")
        c.create<VerbsApi>().get()
        assertEquals("https://api.example/res", cap.url)
    }
    @Test fun `baseUrl trailing slash optional`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://api.example")
        c.create<VerbsApi>().get()
        assertEquals("https://api.example/res", cap.url)
    }
}

class PathTests {
    @Test fun `single placeholder substituted`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().byId(42)
        assertEquals("/posts/42", cap.pathOf().substringAfter("https://example.test"))
    }
    @Test fun `nested placeholders both substituted`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().nested(7, 99)
        assertTrue(cap.pathOf().endsWith("/users/7/posts/99"), cap.pathOf())
    }
    @Test fun `string placeholder substituted`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().string("hello-world")
        assertTrue(cap.pathOf().endsWith("/items/hello-world"))
    }
    @Test fun `placeholder with int converts via toString`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().byId(123456)
        assertTrue("/posts/123456" in cap.url)
    }
    @Test fun `path value with space is URL-encoded`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().string("hello world")
        assertTrue(
            cap.pathOf().endsWith("/items/hello%20world"),
            "expected encoded space, got ${cap.pathOf()}",
        )
    }
    @Test fun `path value with slash is encoded not treated as separator`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().string("a/b")
        assertTrue(
            cap.pathOf().endsWith("/items/a%2Fb"),
            "expected encoded slash, got ${cap.pathOf()}",
        )
    }
    @Test fun `path value with reserved chars is encoded`() = runTest {
        val (c, cap) = mockSetup()
        c.create<PathApi>().string("a?b#c")
        val p = cap.pathOf()
        assertTrue("?" !in p.substringAfter("/items/"), "raw ? leaked into URL: $p")
        assertTrue("#" !in p.substringAfter("/items/"), "raw # leaked into URL: $p")
    }
}

class QueryTests {
    @Test fun `single query param appended`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().single("kotlin")
        assertEquals("kotlin", cap.query("q"))
    }
    @Test fun `multiple query params all present`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().multi(q = "ktor", page = 2, limit = 50)
        assertEquals("ktor", cap.query("q"))
        assertEquals("2", cap.query("page"))
        assertEquals("50", cap.query("limit"))
    }
    @Test fun `null query param is dropped`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().nullable(null)
        assertNull(cap.query("q"))
    }
    @Test fun `non-null nullable query param is sent`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().nullable("present")
        assertEquals("present", cap.query("q"))
    }
    @Test fun `QueryMap entries appended`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().map(mapOf("a" to "1", "b" to 2, "c" to true))
        assertEquals("1", cap.query("a"))
        assertEquals("2", cap.query("b"))
        assertEquals("true", cap.query("c"))
    }
    @Test fun `QueryMap drops null values`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().map(mapOf("present" to "yes", "absent" to null))
        assertEquals("yes", cap.query("present"))
        assertNull(cap.query("absent"))
    }
}

class HeaderTests {
    @Test fun `single Header added`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().single("token-abc")
        assertEquals("token-abc", cap.header("X-Token"))
    }
    @Test fun `null Header is dropped`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().nullable(null)
        assertNull(cap.header("X-Token"))
    }
    @Test fun `Headers static values added`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().staticHeaders()
        assertEquals("alpha", cap.header("X-A"))
        assertEquals("beta", cap.header("X-B"))
    }
    @Test fun `HeaderMap merges into request`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().headerMap(mapOf("X-One" to "1", "X-Two" to 2))
        assertEquals("1", cap.header("X-One"))
        assertEquals("2", cap.header("X-Two"))
    }
    @Test fun `HeaderMap null values dropped`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().headerMap(mapOf("X-Yes" to "y", "X-No" to null))
        assertEquals("y", cap.header("X-Yes"))
        assertNull(cap.header("X-No"))
    }
    @Test fun `static + dynamic + map all coexist`() = runTest {
        val (c, cap) = mockSetup()
        c.create<HeaderApi>().mixed(dyn = "d", extra = mapOf("X-Extra" to "e"))
        assertEquals("s", cap.header("X-Static"))
        assertEquals("d", cap.header("X-Dyn"))
        assertEquals("e", cap.header("X-Extra"))
    }
}
