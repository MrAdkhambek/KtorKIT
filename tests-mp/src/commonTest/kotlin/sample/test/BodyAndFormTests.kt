package sample.test

import io.ktorkit.create
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyTests {
    @Test
    fun string_body_verbatim() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("hello-payload")
        assertEquals("hello-payload", cap.body)
    }

    @Test
    fun string_body_uses_post() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("x")
        assertEquals("POST", cap.method)
    }

    @Test
    fun large_body_roundtrips() = runTest {
        val (c, cap) = mockSetup()
        val payload = "x".repeat(10_000)
        c.create<BodyApi>().stringBody(payload)
        assertEquals(payload, cap.body)
    }

    @Test
    fun serializable_body_json() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().objectBody(Post(id = 7, title = "hello"))
        assertEquals("""{"id":7,"title":"hello"}""", cap.body)
    }

    @Test
    fun serializable_body_content_type() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().objectBody(Post(id = 1, title = "x"))
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("application/json") == true, "Content-Type=$ct")
    }

    @Test
    fun list_body_json_array() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().listBody(listOf(Post(1, "a"), Post(2, "b")))
        assertEquals("""[{"id":1,"title":"a"},{"id":2,"title":"b"}]""", cap.body)
    }

    @Test
    fun map_body_json_object() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().mapBody(mapOf("k" to "v"))
        assertEquals("""{"k":"v"}""", cap.body)
    }

    @Test
    fun nullable_body_null() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().nullableBody(null)
        assertEquals("null", cap.body)
    }

    @Test
    fun nullable_body_value() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().nullableBody(Post(3, "z"))
        assertEquals("""{"id":3,"title":"z"}""", cap.body)
    }

    @Test
    fun put_with_body() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().putBody(Post(5, "p"))
        assertEquals("PUT", cap.method)
        assertEquals("""{"id":5,"title":"p"}""", cap.body)
    }

    @Test
    fun delete_with_body() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().deleteBody(Post(6, "d"))
        assertEquals("DELETE", cap.method)
        assertEquals("""{"id":6,"title":"d"}""", cap.body)
    }

    @Test
    fun string_body_not_json_encoded() = runTest {
        val (c, cap) = mockSetup()
        c.create<BodyApi>().stringBody("plain")
        assertEquals("plain", cap.body)
    }
}

class FormTests {
    @Test
    fun simple_form_fields() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "ada", age = 207)
        assertTrue("name=ada" in cap.body, cap.body)
        assertTrue("age=207" in cap.body, cap.body)
    }

    @Test
    fun form_content_type() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "x", age = 1)
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("x-www-form-urlencoded") == true, "Content-Type=$ct")
    }

    @Test
    fun form_null_field_dropped() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().nullableField(n = "ada", nick = null)
        assertTrue("name=ada" in cap.body)
        assertFalse("nickname" in cap.body, cap.body)
    }

    @Test
    fun form_nullable_field_present() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().nullableField(n = "ada", nick = "lovelace")
        assertTrue("nickname=lovelace" in cap.body, cap.body)
    }

    @Test
    fun form_url_encoded_values() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().simple(n = "ada with spaces", age = 1)
        assertTrue(
            ("name=ada+with+spaces" in cap.body) || ("name=ada%20with%20spaces" in cap.body),
            cap.body,
        )
    }

    @Test
    fun field_map_entries() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("a" to "1", "b" to 2, "c" to true))
        assertTrue("a=1" in cap.body, cap.body)
        assertTrue("b=2" in cap.body, cap.body)
        assertTrue("c=true" in cap.body, cap.body)
    }

    @Test
    fun field_map_drops_nulls() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("present" to "yes", "absent" to null))
        assertTrue("present=yes" in cap.body, cap.body)
        assertFalse("absent" in cap.body, cap.body)
    }

    @Test
    fun field_and_field_map_coexist() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldAndMap(n = "ada", extra = mapOf("role" to "admin"))
        assertTrue("name=ada" in cap.body, cap.body)
        assertTrue("role=admin" in cap.body, cap.body)
    }

    @Test
    fun field_map_content_type() = runTest {
        val (c, cap) = mockSetup()
        c.create<FormApi>().fieldMap(mapOf("a" to "1"))
        val ct = cap.header("Content-Type")
        assertTrue(ct?.contains("x-www-form-urlencoded") == true, "Content-Type=$ct")
    }
}
