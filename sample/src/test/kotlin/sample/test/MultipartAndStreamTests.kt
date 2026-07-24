package sample.test

import io.ktorkit.create
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ktor renders part names unquoted (`name=foo`); tolerate either form. */
private fun String.hasPartNamed(name: String): Boolean =
    Regex("""name="?${Regex.escape(name)}"?[;\r\n]""").containsMatchIn(this)

class MultipartTests {
    @Test fun `text parts appear in multipart body`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "ada", age = 207)
        assertTrue(cap.body.hasPartNamed("name"), cap.body)
        assertTrue("ada" in cap.body, cap.body)
        assertTrue(cap.body.hasPartNamed("age"), cap.body)
        assertTrue("207" in cap.body, cap.body)
    }

    @Test fun `multipart sets multipart form-data content type`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("multipart/form-data") == true, "Content-Type=$ct")
    }

    @Test fun `multipart content type carries a boundary`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("boundary=") == true, "Content-Type=$ct")
    }

    @Test fun `ByteArray part is sent with its filename`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().filePart(meta = "info", file = "file-content".encodeToByteArray())
        assertTrue("filename=\"report.txt\"" in cap.body, cap.body)
        assertTrue("file-content" in cap.body, cap.body)
    }

    @Test fun `ByteArray part coexists with a text part`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().filePart(meta = "info", file = "bytes".encodeToByteArray())
        assertTrue(cap.body.hasPartNamed("meta"), cap.body)
        assertTrue("info" in cap.body, cap.body)
        assertTrue(cap.body.hasPartNamed("file"), cap.body)
    }

    @Test fun `null part is dropped`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().nullablePart(name = "ada", optional = null)
        assertTrue(cap.body.hasPartNamed("name"), cap.body)
        assertFalse("optional" in cap.body, cap.body)
    }

    @Test fun `non-null optional part is sent`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().nullablePart(name = "ada", optional = "extra")
        assertTrue(cap.body.hasPartNamed("optional"), cap.body)
        assertTrue("extra" in cap.body, cap.body)
    }

    @Test fun `multipart uses POST`() = runTest {
        val (c, cap) = mockSetup()
        c.create<MultipartApi>().textParts(name = "x", age = 1)
        assertEquals("POST", cap.method)
    }
}

class StreamingTests {
    private val ndjson = """
        {"id":1,"title":"a"}
        {"id":2,"title":"b"}
        {"id":3,"title":"c"}
    """.trimIndent()

    @Test fun `Flow of Serializable decodes each line`() = runTest {
        val (c, _) = mockSetup(responseBody = ndjson)
        val posts = c.create<StreamApi>().posts().toList()
        assertEquals(3, posts.size)
        assertEquals(1, posts[0].id)
        assertEquals("b", posts[1].title)
        assertEquals(3, posts[2].id)
    }

    @Test fun `Flow of String emits raw lines`() = runTest {
        val (c, _) = mockSetup(responseBody = "alpha\nbeta\ngamma")
        val lines = c.create<StreamApi>().lines().toList()
        assertEquals(listOf("alpha", "beta", "gamma"), lines)
    }

    @Test fun `Flow skips blank lines between records`() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"id\":1,\"title\":\"a\"}\n\n{\"id\":2,\"title\":\"b\"}\n")
        val posts = c.create<StreamApi>().posts().toList()
        assertEquals(2, posts.size)
    }

    @Test fun `empty stream yields empty list`() = runTest {
        val (c, _) = mockSetup(responseBody = "")
        assertEquals(0, c.create<StreamApi>().posts().toList().size)
    }

    @Test fun `single record stream`() = runTest {
        val (c, _) = mockSetup(responseBody = """{"id":42,"title":"only"}""")
        val posts = c.create<StreamApi>().posts().toList()
        assertEquals(1, posts.size)
        assertEquals(42, posts[0].id)
    }

    @Test fun `Flow of Map decodes each line`() = runTest {
        val (c, _) = mockSetup(responseBody = "{\"a\":1}\n{\"b\":2,\"c\":3}")
        val maps = c.create<StreamApi>().maps().toList()
        assertEquals(2, maps.size)
        assertEquals(1, maps[0]["a"])
        assertEquals(3, maps[1]["c"])
    }

    @Test fun `Flow issues the request lazily on collection`() = runTest {
        val (c, cap) = mockSetup(responseBody = """{"id":1,"title":"a"}""")
        val flow = c.create<StreamApi>().posts()
        assertEquals("", cap.method, "request should not be sent before collection")
        flow.toList()
        assertEquals("GET", cap.method)
    }

    @Test fun `Flow can be collected twice issuing two requests`() = runTest {
        val (c, cap) = mockSetup(responseBody = """{"id":1,"title":"a"}""")
        val flow = c.create<StreamApi>().posts()
        assertEquals(1, flow.toList().size)
        assertEquals(1, flow.toList().size)
        assertEquals("GET", cap.method)
    }
}
