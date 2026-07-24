import com.adkhambek.ktor.kit.KtorClient
import com.adkhambek.ktor.kit.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTest {
    @Test
    fun generated_impl_works_in_common_code() = runTest {
        val http = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel("""{"id":7,"title":"kmp"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        val api = KtorClient("https://example.test/", http).create<PostsApi>()
        val post = api.getPost(7)
        assertEquals(7, post.id)
        assertEquals("kmp", post.title)
    }
}
