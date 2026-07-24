package sample.test

import io.ktorkit.create
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlTests {
    @Test fun `Url overrides path entirely`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://default.test/")
        c.create<UrlApi>().absolute("https://override.test/special/path")
        assertEquals("https://override.test/special/path", cap.url)
    }

    @Test fun `Url overrides baseUrl on typed return`() = runTest {
        val (c, cap) = mockSetup(
            baseUrl = "https://default.test/",
            responseBody = """{"id":42,"title":"t"}""",
        )
        val p = c.create<UrlApi>().absoluteTyped("https://override.test/posts/42")
        assertEquals(42, p.id)
        assertEquals("https://override.test/posts/42", cap.url)
    }

    @Test fun `non-Url method still uses baseUrl`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://default.test/")
        c.create<UrlApi>().normal()
        assertTrue(cap.url.startsWith("https://default.test/"), cap.url)
    }
}

class DeclaredBaseUrlTests {
    @Test fun `ContributesAPI baseUrl wins over empty client baseUrl`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "")
        c.create<DeclaredBaseApi>().get(7)
        assertEquals("https://api.declared.example/posts/7", cap.url)
    }

    @Test fun `ContributesAPI baseUrl wins over different client baseUrl`() = runTest {
        val (c, cap) = mockSetup(baseUrl = "https://other.test/")
        c.create<DeclaredBaseApi>().get(1)
        assertEquals("https://api.declared.example/posts/1", cap.url)
    }

    @Test fun `Declared baseUrl works with typed list return`() = runTest {
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
    @Test fun `path + query combined correctly`() = runTest {
        val (c, cap) = mockSetup()
        c.create<QueryApi>().pathAndQuery(k = "books", q = "kotlin")
        assertTrue(cap.url.contains("/books/search"), cap.url)
        assertEquals("kotlin", cap.query("q"))
    }
}
