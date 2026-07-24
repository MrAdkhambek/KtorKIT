package sample.test

import com.adkhambek.ktor.kit.create
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyTests {
    @Test fun `string Body is sent verbatim`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("hello-payload")
        assertEquals("hello-payload", cap.body)
    }
    @Test fun `string Body uses POST method`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("x")
        assertEquals("POST", cap.method)
    }
    @Test fun `large string Body roundtrips`() = runTest {
        val (c, cap) = mockSetup()
        val payload = "x".repeat(10_000)
        c.create<BodyApi>().stringBody(payload)
        assertEquals(payload, cap.body)
    }
    @Test fun `Serializable Body is encoded as JSON`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().objectBody(Post(id = 7, title = "hello"))
        assertEquals("""{"id":7,"title":"hello"}""", cap.body)
    }
    @Test fun `Serializable Body sets JSON content type`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().objectBody(Post(id = 1, title = "x"))
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("application/json") == true, "Content-Type=$ct")
    }
    @Test fun `List Body is encoded as JSON array`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().listBody(listOf(Post(1, "a"), Post(2, "b")))
        assertEquals("""[{"id":1,"title":"a"},{"id":2,"title":"b"}]""", cap.body)
    }
    @Test fun `Map Body is encoded as JSON object`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().mapBody(mapOf("k" to "v"))
        assertEquals("""{"k":"v"}""", cap.body)
    }
    @Test fun `nullable Body encodes null`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().nullableBody(null)
        assertEquals("null", cap.body)
    }
    @Test fun `nullable Body encodes value when present`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().nullableBody(Post(3, "z"))
        assertEquals("""{"id":3,"title":"z"}""", cap.body)
    }
    @Test fun `PUT with Serializable Body`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().putBody(Post(5, "p"))
        assertEquals("PUT", cap.method)
        assertEquals("""{"id":5,"title":"p"}""", cap.body)
    }
    @Test fun `DELETE with Serializable Body`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().deleteBody(Post(6, "d"))
        assertEquals("DELETE", cap.method)
        assertEquals("""{"id":6,"title":"d"}""", cap.body)
    }
    @Test fun `String Body is not JSON-encoded`() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("plain")
        assertEquals("plain", cap.body)
    }
}

class FormTests {
    @Test fun `simple form fields URL-encoded in body`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "ada", age = 207)
        assertTrue("name=ada" in cap.body, cap.body)
        assertTrue("age=207" in cap.body, cap.body)
    }
    @Test fun `form sets content-type to form-urlencoded`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "x", age = 1)
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("x-www-form-urlencoded") == true, "Content-Type=$ct")
    }
    @Test fun `form null field is dropped`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().nullableField(n = "ada", nick = null)
        assertTrue("name=ada" in cap.body)
        assertFalse("nickname" in cap.body, cap.body)
    }
    @Test fun `form non-null nullable field is sent`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().nullableField(n = "ada", nick = "lovelace")
        assertTrue("nickname=lovelace" in cap.body, cap.body)
    }
    @Test fun `form values URL-encoded`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "ada with spaces", age = 1)
        assertTrue(
            ("name=ada+with+spaces" in cap.body) || ("name=ada%20with%20spaces" in cap.body),
            cap.body,
        )
    }
    @Test fun `FieldMap entries become form fields`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("a" to "1", "b" to 2, "c" to true))
        assertTrue("a=1" in cap.body, cap.body)
        assertTrue("b=2" in cap.body, cap.body)
        assertTrue("c=true" in cap.body, cap.body)
    }
    @Test fun `FieldMap drops null values`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("present" to "yes", "absent" to null))
        assertTrue("present=yes" in cap.body, cap.body)
        assertFalse("absent" in cap.body, cap.body)
    }
    @Test fun `Field and FieldMap coexist`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldAndMap(n = "ada", extra = mapOf("role" to "admin"))
        assertTrue("name=ada" in cap.body, cap.body)
        assertTrue("role=admin" in cap.body, cap.body)
    }
    @Test fun `FieldMap sets form-urlencoded content type`() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("a" to "1"))
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("x-www-form-urlencoded") == true, "Content-Type=$ct")
    }
}
