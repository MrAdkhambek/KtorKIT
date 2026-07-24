package sample.test

import io.ktorkit.create
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ktor renders part names unquoted (`name=foo`); tolerate either form. */
private fun String.hasPartNamed(name: String): Boolean =
    Regex("""name="?${Regex.escape(name)}"?[;\r\n]""").containsMatchIn(this)

class MultipartTests {
    @Test fun text_parts_in_body() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "ada", age = 207)
        assertTrue(cap.body.hasPartNamed("name"), cap.body)
        assertTrue("ada" in cap.body, cap.body)
        assertTrue(cap.body.hasPartNamed("age"), cap.body)
        assertTrue("207" in cap.body, cap.body)
    }

    @Test fun multipart_content_type() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("multipart/form-data") == true, "Content-Type=$ct")
    }

    @Test fun multipart_boundary_present() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        assertTrue(cap.header("Content-Type")?.contains("boundary=") == true)
    }

    @Test fun byte_array_part_filename() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().filePart(meta = "info", file = "file-content".encodeToByteArray())
        assertTrue("filename=\"report.txt\"" in cap.body, cap.body)
        assertTrue("file-content" in cap.body, cap.body)
    }

    @Test fun byte_array_part_with_text_part() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().filePart(meta = "info", file = "bytes".encodeToByteArray())
        assertTrue(cap.body.hasPartNamed("meta"), cap.body)
        assertTrue("info" in cap.body, cap.body)
        assertTrue(cap.body.hasPartNamed("file"), cap.body)
    }

    @Test fun null_part_dropped() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().nullablePart(name = "ada", optional = null)
        assertTrue(cap.body.hasPartNamed("name"), cap.body)
        assertFalse("optional" in cap.body, cap.body)
    }

    @Test fun non_null_optional_part_sent() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().nullablePart(name = "ada", optional = "extra")
        assertTrue(cap.body.hasPartNamed("optional"), cap.body)
        assertTrue("extra" in cap.body, cap.body)
    }

    @Test fun multipart_uses_post() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        assertEquals("POST", cap.method)
    }
}

class StreamingTests {
    private val ndjson = "{\"id\":1,\"title\":\"a\"}\n{\"id\":2,\"title\":\"b\"}\n{\"id\":3,\"title\":\"c\"}"

    @Test fun flow_of_serializable() = runTest {
        val (c, _) = mockSetup(responseBody = ndjson)
        val posts = c.create<StreamApi>().posts().toList()
        assertEquals(3, posts.size)
        assertEquals(1, posts[0].id)
        assertEquals("b", posts[1].title)
    }

    @Test fun flow_of_string() = runTest {
        val (c, _) = mockSetup(responseBody = "alpha\nbeta\ngamma")
        assertEquals(listOf("alpha", "beta", "gamma"), c.create<StreamApi>().lines().toList())
    }

    @Test fun flow_skips_blank_lines() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}\n\n{\"id\":2,\"title\":\"b\"}\n")
        assertEquals(2, c.create<StreamApi>().posts().toList().size)
    }

    @Test fun empty_stream() = runTest {
        val (c, _) = mockSetup(responseBody = "")
        assertEquals(0, c.create<StreamApi>().posts().toList().size)
    }

    @Test fun single_record_stream() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":42,\"title\":\"only\"}")
        val posts = c.create<StreamApi>().posts().toList()
        assertEquals(1, posts.size)
        assertEquals(42, posts[0].id)
    }

    @Test fun flow_of_map() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"a\":1}\n{\"b\":2,\"c\":3}")
        val maps = c.create<StreamApi>().maps().toList()
        assertEquals(2, maps.size)
        assertEquals(3, maps[1]["c"])
    }

    @Test fun flow_is_lazy() = runTest {
        val (c, cap) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}")
        val flow = c.create<StreamApi>().posts()
        assertEquals("", cap.method, "request should not be sent before collection")
        flow.toList()
        assertEquals("GET", cap.method)
    }

    @Test fun flow_collectable_twice() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}")
        val flow = c.create<StreamApi>().posts()
        assertEquals(1, flow.toList().size)
        assertEquals(1, flow.toList().size)
    }
}
