package sample.test

import com.adkhambek.ktor.kit.create
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerbTests {
    @Test fun get_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().get(); assertEquals("GET", cap.method)
    }
    @Test fun post_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().post(); assertEquals("POST", cap.method)
    }
    @Test fun put_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().put(); assertEquals("PUT", cap.method)
    }
    @Test fun delete_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().delete(); assertEquals("DELETE", cap.method)
    }
    @Test fun patch_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().patch(); assertEquals("PATCH", cap.method)
    }
    @Test fun head_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().head(); assertEquals("HEAD", cap.method)
    }
    @Test fun options_method() = runTest {
        val (c, cap) = mockSetup(); c.create<VerbsApi>().options(); assertEquals("OPTIONS", cap.method)
    }
    @Test fun url_is_base_plus_path() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://api.example/")
        c.create<VerbsApi>().get()
        assertEquals("https://api.example/res", cap.url)
    }
    @Test fun trailing_slash_optional() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://api.example")
        c.create<VerbsApi>().get()
        assertEquals("https://api.example/res", cap.url)
    }
}

class PathTests {
    @Test fun single_placeholder() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().byId(42)
        assertEquals("/posts/42", cap.pathOf().substringAfter("https://example.test"))
    }
    @Test fun nested_placeholders() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().nested(7, 99)
        assertTrue(cap.pathOf().endsWith("/users/7/posts/99"), cap.pathOf())
    }
    @Test fun string_placeholder() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().string("hello-world")
        assertTrue(cap.pathOf().endsWith("/items/hello-world"))
    }
    @Test fun int_placeholder_via_toString() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().byId(123456)
        assertTrue("/posts/123456" in cap.url)
    }
    @Test fun path_value_space_encoded() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().string("hello world")
        assertTrue(
            cap.pathOf().endsWith("/items/hello%20world"),
            "expected encoded space, got ${cap.pathOf()}",
        )
    }
    @Test fun path_value_slash_encoded() = runTest {
        val (c, cap) = mockSetup(); c.create<PathApi>().string("a/b")
        assertTrue(
            cap.pathOf().endsWith("/items/a%2Fb"),
            "expected encoded slash, got ${cap.pathOf()}",
        )
    }
}

class QueryTests {
    @Test fun single_query() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().single("kotlin")
        assertEquals("kotlin", cap.query("q"))
    }
    @Test fun multiple_query() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().multi(q = "ktor", page = 2, limit = 50)
        assertEquals("ktor", cap.query("q"))
        assertEquals("2", cap.query("page"))
        assertEquals("50", cap.query("limit"))
    }
    @Test fun null_query_dropped() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().nullable(null)
        assertNull(cap.query("q"))
    }
    @Test fun nullable_query_present() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().nullable("present")
        assertEquals("present", cap.query("q"))
    }
    @Test fun query_map() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().map(mapOf("a" to "1", "b" to 2, "c" to true))
        assertEquals("1", cap.query("a"))
        assertEquals("2", cap.query("b"))
        assertEquals("true", cap.query("c"))
    }
    @Test fun query_map_null_dropped() = runTest {
        val (c, cap) = mockSetup(); c.create<QueryApi>().map(mapOf("present" to "yes", "absent" to null))
        assertEquals("yes", cap.query("present"))
        assertNull(cap.query("absent"))
    }
}

class HeaderTests {
    @Test fun single_header() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().single("token-abc")
        assertEquals("token-abc", cap.header("X-Token"))
    }
    @Test fun null_header_dropped() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().nullable(null)
        assertNull(cap.header("X-Token"))
    }
    @Test fun static_headers() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().staticHeaders()
        assertEquals("alpha", cap.header("X-A"))
        assertEquals("beta", cap.header("X-B"))
    }
    @Test fun header_map_merges() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().headerMap(mapOf("X-One" to "1", "X-Two" to 2))
        assertEquals("1", cap.header("X-One"))
        assertEquals("2", cap.header("X-Two"))
    }
    @Test fun header_map_null_dropped() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().headerMap(mapOf("X-Yes" to "y", "X-No" to null))
        assertEquals("y", cap.header("X-Yes"))
        assertNull(cap.header("X-No"))
    }
    @Test fun mixed_static_dynamic_map() = runTest {
        val (c, cap) = mockSetup(); c.create<HeaderApi>().mixed(dyn = "d", extra = mapOf("X-Extra" to "e"))
        assertEquals("s", cap.header("X-Static"))
        assertEquals("d", cap.header("X-Dyn"))
        assertEquals("e", cap.header("X-Extra"))
    }
}
