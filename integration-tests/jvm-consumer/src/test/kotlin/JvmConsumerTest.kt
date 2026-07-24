import com.adkhambek.ktor.kit.Body
import com.adkhambek.ktor.kit.ContributesAPI
import com.adkhambek.ktor.kit.DELETE
import com.adkhambek.ktor.kit.GET
import com.adkhambek.ktor.kit.KtorClient
import com.adkhambek.ktor.kit.Multipart
import com.adkhambek.ktor.kit.POST
import com.adkhambek.ktor.kit.Part
import com.adkhambek.ktor.kit.Path
import com.adkhambek.ktor.kit.Response
import com.adkhambek.ktor.kit.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
data class Post(val id: Int, val title: String)

@ContributesAPI
interface PostsApi {
    @GET("posts/{id}") suspend fun get(@Path("id") id: Int): Post
    @GET("posts") suspend fun wrapped(): Response<List<Post>>
    @POST("posts") suspend fun create(@Body post: Post): String
    @DELETE("posts/{id}") suspend fun delete(@Path("id") id: Int)
    @Multipart @POST("upload") suspend fun upload(
        @Part("meta") meta: String,
        @Part(value = "file", fileName = "a.txt") file: ByteArray,
    ): String
    @GET("stream") fun stream(): Flow<Post>
}

/**
 * Exercises the path a real consumer takes: the published Gradle plugin applied to a
 * plain `kotlin("jvm")` project, with the runtime pulled in by the plugin rather than
 * declared by hand.
 */
class JvmConsumerTest {

    private fun api(): PostsApi {
        val http = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val body = when {
                        request.url.encodedPath.endsWith("/stream") ->
                            "{\"id\":1,\"title\":\"a\"}\n{\"id\":2,\"title\":\"b\"}"
                        request.url.encodedPath.endsWith("/posts") && request.method.value == "GET" ->
                            """[{"id":1,"title":"first"}]"""
                        request.method.value in setOf("POST", "DELETE") -> "ok"
                        else -> """{"id":7,"title":"seven"}"""
                    }
                    respond(
                        content = ByteReadChannel(body),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        return KtorClient("https://example.test/", http).create()
    }

    @Test fun typed_get() = runTest {
        val post = api().get(7)
        assertEquals(7, post.id)
        assertEquals("seven", post.title)
    }

    @Test fun response_wrapper() = runTest {
        val r = api().wrapped()
        assertTrue(r.isSuccess)
        assertEquals("first", r.body.single().title)
    }

    @Test fun serializable_body() = runTest {
        assertEquals("ok", api().create(Post(1, "hello")))
    }

    @Test fun unit_return() = runTest {
        api().delete(1)
    }

    @Test fun multipart_upload() = runTest {
        assertEquals("ok", api().upload("m", "bytes".encodeToByteArray()))
    }

    @Test fun flow_streaming() = runTest {
        assertEquals(listOf(1, 2), api().stream().toList().map { it.id })
    }
}
