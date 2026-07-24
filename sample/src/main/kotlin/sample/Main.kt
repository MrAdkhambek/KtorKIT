package sample

import io.ktorkit.Body
import io.ktorkit.ContributesAPI
import io.ktorkit.Field
import io.ktorkit.FieldMap
import io.ktorkit.FormUrlEncoded
import io.ktorkit.GET
import io.ktorkit.Header
import io.ktorkit.HeaderMap
import io.ktorkit.Headers
import io.ktorkit.KtorClient
import io.ktorkit.Multipart
import io.ktorkit.PATCH
import io.ktorkit.Part
import io.ktorkit.POST
import io.ktorkit.Path
import io.ktorkit.Query
import io.ktorkit.QueryMap
import io.ktorkit.Response
import io.ktorkit.Url
import io.ktorkit.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class HttpBinResponse(
    val url: String,
    val method: String,
    val args: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val data: String = "",
    val form: Map<String, String> = emptyMap(),
    val files: Map<String, String> = emptyMap(),
)

/** One record from httpbin's `/stream/{n}` newline-delimited JSON endpoint. */
@Serializable
data class StreamRecord(val id: Int, val url: String)

@Serializable
data class JsonPlaceholderPost(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
)

@Serializable
data class CreateItem(val name: String, val qty: Int)

@ContributesAPI
interface HttpBinApi {

    @GET("anything/{slug}")
    suspend fun typedAnything(
        @Path("slug") slug: String,
        @Query("q") q: String,
    ): HttpBinResponse

    @PATCH("anything")
    suspend fun patchAnything(
        @Body body: String,
    ): HttpBinResponse

    @Headers("X-Static-A: alpha")
    @GET("anything/withheaders")
    suspend fun withHeaders(
        @Header("X-Token") token: String,
        @HeaderMap extra: Map<String, Any?>,
        @QueryMap params: Map<String, Any?>,
    ): HttpBinResponse

    @FormUrlEncoded
    @POST("anything")
    suspend fun submitForm(
        @Field("name") name: String,
        @Field("age") age: Int,
    ): HttpBinResponse

    @POST("anything")
    suspend fun postJson(@Body payload: CreateItem): HttpBinResponse

    @FormUrlEncoded
    @POST("anything")
    suspend fun submitFieldMap(@FieldMap fields: Map<String, Any?>): HttpBinResponse

    @Multipart
    @POST("anything")
    suspend fun upload(
        @Part("meta") meta: String,
        @Part(value = "file", fileName = "notes.txt") file: ByteArray,
    ): HttpBinResponse

    @GET("stream/{n}")
    fun streamRecords(@Path("n") n: Int): Flow<StreamRecord>

    @GET("status/{code}")
    suspend fun statusEndpoint(@Path("code") code: Int): Response<String>

    @GET("anything/wrapped")
    suspend fun wrappedTyped(@Query("q") q: String): Response<HttpBinResponse>

    @GET("response-headers")
    suspend fun mapResponse(@Query("X-Greeting") greeting: String): Map<String, String>
}

@ContributesAPI(baseUrl = "https://jsonplaceholder.typicode.com/")
interface JsonPlaceholderApi {
    @GET("posts")
    suspend fun listPosts(): List<JsonPlaceholderPost>

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): JsonPlaceholderPost

    @GET("")
    suspend fun fetchAbsolute(@Url url: String): JsonPlaceholderPost
}

fun main(): Unit = runBlocking {
    val engine = HttpClient(CIO)
    val httpbin = KtorClient(baseUrl = "https://httpbin.org/", httpClient = engine).create<HttpBinApi>()
    // JsonPlaceholderApi declares its own baseUrl via @ContributesAPI — pass empty
    val jp = KtorClient(baseUrl = "", httpClient = engine).create<JsonPlaceholderApi>()

    println("--- typed GET ---")
    val typed = httpbin.typedAnything(slug = "typed", q = "world")
    check(typed.method == "GET")
    check(typed.args["q"] == "world")
    println("✓ JSON deserialized into HttpBinResponse")

    println()
    println("--- @PATCH (Body) ---")
    val patched = httpbin.patchAnything(body = "patch-payload")
    check(patched.method == "PATCH")
    check(patched.data == "patch-payload")
    println("✓ PATCH echoed body")

    println()
    println("--- headers + maps ---")
    val withH = httpbin.withHeaders(
        token = "secret-123",
        extra = mapOf("X-Extra" to "x"),
        params = mapOf("filter" to "active"),
    )
    check(withH.headers["X-Token"] == "secret-123")
    check(withH.headers["X-Static-A"] == "alpha")
    check(withH.args["filter"] == "active")
    println("✓ all header/map paths echoed")

    println()
    println("--- @FormUrlEncoded + @Field ---")
    val form = httpbin.submitForm(name = "ada", age = 207)
    println("form=${form.form}")
    check(form.form["name"] == "ada")
    check(form.form["age"] == "207")
    println("✓ form fields URL-encoded and posted")

    println()
    println("--- @Body with a @Serializable type (auto-JSON) ---")
    val posted = httpbin.postJson(CreateItem(name = "widget", qty = 3))
    println("echoed data=${posted.data}")
    check(posted.data == """{"name":"widget","qty":3}""")
    check(posted.headers["Content-Type"]?.startsWith("application/json") == true)
    println("✓ object serialized to JSON with application/json content type")

    println()
    println("--- @FieldMap ---")
    val mapForm = httpbin.submitFieldMap(mapOf("city" to "Tashkent", "zip" to 100000, "skip" to null))
    println("form=${mapForm.form}")
    check(mapForm.form["city"] == "Tashkent")
    check(mapForm.form["zip"] == "100000")
    check("skip" !in mapForm.form)
    println("✓ dynamic form fields sent; nulls dropped")

    println()
    println("--- @Multipart + @Part (text + file upload) ---")
    val uploaded = httpbin.upload(
        meta = "release-notes",
        file = "line one\nline two".encodeToByteArray(),
    )
    println("form=${uploaded.form} files=${uploaded.files.keys}")
    check(uploaded.form["meta"] == "release-notes")
    check(uploaded.files["file"]?.contains("line one") == true)
    println("✓ text part and named file part both uploaded")

    println()
    println("--- Flow<T> streaming (newline-delimited JSON) ---")
    val streamed = httpbin.streamRecords(n = 5).toList()
    println("received ${streamed.size} records, ids=${streamed.map { it.id }}")
    check(streamed.size == 5)
    check(streamed.map { it.id } == listOf(0, 1, 2, 3, 4))
    println("✓ response streamed and decoded line by line")

    println()
    println("--- @Path URL-encoding ---")
    // httpbin echoes the *decoded* URL, so the wire-level percent-encoding is
    // asserted in the unit tests. What this proves end-to-end is that a value
    // containing a space and a slash survives the round trip intact.
    val encoded = httpbin.typedAnything(slug = "a b/c", q = "x")
    println("url=${encoded.url}")
    check(encoded.url.substringBefore('?').endsWith("/anything/a b/c"))
    check(encoded.args["q"] == "x")
    println("✓ path value round-tripped intact; query still parsed separately")

    println()
    println("--- List<JsonPlaceholderPost> return ---")
    val posts = jp.listPosts()
    println("got ${posts.size} posts; first.title=\"${posts.first().title.take(40)}…\"")
    check(posts.size == 100)
    check(posts.first().id == 1)
    println("✓ List<T> deserialized")

    println()
    println("--- single typed return ---")
    val one = jp.getPost(id = 7)
    check(one.id == 7)
    println("✓ single Post#7 fetched: ${one.title.take(40)}…")

    println()
    println("--- Response<String> (status code access) ---")
    val r404: Response<String> = httpbin.statusEndpoint(code = 404)
    println("status=${r404.status.value} success=${r404.isSuccess}")
    check(r404.status.value == 404)
    check(!r404.isSuccess)
    val r200: Response<String> = httpbin.statusEndpoint(code = 200)
    check(r200.isSuccess)
    println("✓ status codes accessible without runtime guesswork")

    println()
    println("--- Response<HttpBinResponse> (status + decoded body + headers) ---")
    val wrapped = httpbin.wrappedTyped(q = "wrapped-test")
    println("status=${wrapped.status.value}")
    println("server-header content-type=${wrapped.headers["Content-Type"]}")
    println("body.method=${wrapped.body.method}")
    check(wrapped.isSuccess)
    check(wrapped.body.args["q"] == "wrapped-test")
    check(wrapped.headers["Content-Type"]?.startsWith("application/json") == true)
    println("✓ wrapped Response gives status + headers + decoded body together")

    println()
    println("--- Map<String, String> return ---")
    val mapResp = httpbin.mapResponse(greeting = "hello-from-ktorkit")
    println("keys=${mapResp.keys}")
    check(mapResp["X-Greeting"] == "hello-from-ktorkit")
    println("✓ Map<K, V> deserialized via MapSerializer + String.serializer()")

    println()
    println("--- @ContributesAPI(baseUrl = ...) — JsonPlaceholderApi has no client baseUrl ---")
    val first = jp.listPosts().first()
    check(first.id == 1)
    println("✓ Per-API baseUrl from annotation works (KtorClient.baseUrl = \"\")")

    println()
    println("--- @Url absolute URL parameter ---")
    val absolute = jp.fetchAbsolute(url = "https://jsonplaceholder.typicode.com/posts/42")
    check(absolute.id == 42)
    println("✓ @Url overrides path/baseUrl entirely: post #${absolute.id}")
}
