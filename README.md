# KtorKIT

A Kotlin Compiler Plugin that turns annotated interfaces into type-safe [Ktor](https://ktor.io/) HTTP clients — inspired by Retrofit, powered by K2 FIR and IR.

Define your API as an interface, annotate methods with HTTP verbs, and let the compiler generate the implementation at compile time. No reflection, no code generation task, no annotation processor — just a compiler plugin.

## Features

- Compile-time implementation generation via K2 FIR + IR
- Kotlin Multiplatform: JVM, JS, and 12 Kotlin/Native targets
- All HTTP verbs: `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`
- Path parameters with `@Path` (percent-encoded automatically)
- Query parameters with `@Query` and `@QueryMap`
- Request headers with `@Header`, `@Headers`, and `@HeaderMap`
- Request bodies with `@Body` — `@Serializable` types are encoded to JSON automatically
- Form-encoded requests with `@FormUrlEncoded`, `@Field`, and `@FieldMap`
- File uploads and mixed form data with `@Multipart` and `@Part`
- Streaming responses as `Flow<T>` — decoded line by line, nothing buffered up front
- Dynamic URLs with `@Url`
- Per-API base URLs via `@ContributesAPI(baseUrl = "...")`
- Automatic JSON serialization and deserialization via `kotlinx.serialization`
- Rich return types: `T`, `T?`, `List<T>`, `Map<K, V>`, `Response<T>`, `Flow<T>`, `String`
- Compile-time diagnostics for common mistakes (missing verbs, mismatched path placeholders, misuse of form/multipart annotations, non-serializable types)

## Versions

| Component                       | Version         |
|---------------------------------|-----------------|
| **KtorKIT**                     | `0.1.0-SNAPSHOT`|
| **Kotlin**                      | `2.4.10`        |
| **Ktor**                        | `3.5.1`         |
| **kotlinx-serialization-json**  | `1.11.0`        |
| **kotlinx-coroutines**          | `1.11.0`        |
| **Gradle**                      | `9.6.1`         |
| **JVM Toolchain**               | `21`            |

Dependency versions live in `gradle/libs.versions.toml`; every module resolves them from
that catalog. The published artifact version is separate — it is defined once as
`ktorkitVersion` in `gradle.properties`, which the root build script and the Gradle plugin's
baked-in artifact coordinates both read from.

Because the plugin binds to compiler internals, **the Kotlin version is not a free choice**:
KtorKIT must be built against the same Kotlin version your project compiles with.

## Supported Platforms

| Tier    | Targets                                                                 |
|---------|-------------------------------------------------------------------------|
| JVM     | `jvm`                                                                   |
| JS      | `js(IR)` — Node.js                                                      |
| Apple   | `iosX64`, `iosArm64`, `iosSimulatorArm64`, `macosX64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64` |
| Other   | `linuxX64`, `mingwX64`                                                  |

## Project Structure

```
ktorkit/
├── runtime/                      # What your code links against
│   ├── Annotations.kt            #   @GET, @Path, @Body, @Part, …
│   ├── KtorClient.kt             #   baseUrl + HttpClient pair
│   ├── Create.kt                 #   create<T>() — rewritten at every call site
│   ├── Execute.kt                #   RequestBuilder: the API generated code targets
│   └── Response.kt               #   status + headers + decoded body
│
├── compiler/                     # The K2 plugin itself
│   ├── KtorKitCompilerPluginRegistrar.kt   # entry point (META-INF/services)
│   ├── KtorKitFirExtensionRegistrar.kt     # registers the two FIR extensions
│   ├── KtorKitDeclarationGenerationExtension.kt  # FIR: declares PostsApi.Impl
│   ├── KtorKitCheckers.kt                  # FIR: validation
│   ├── Diagnostics.kt                      # FIR: error definitions + messages
│   ├── GeneratedBodyFiller.kt              # IR: writes the method bodies
│   ├── CreateCallTransformer.kt            # IR: create<T>() → Impl(client)
│   └── KtorKitPluginKey.kt                 # tags every generated declaration
│
├── gradle-plugin/                # Wires the compiler plugin into kotlinc
├── sample/                       # Runnable demo + JVM and diagnostic tests
└── tests-mp/                     # The same suite on JVM, JS, and native
```

## Quick Start

### 1. Apply the Gradle plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()          // resolves the io.ktorkit plugin itself
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()          // the plugin adds io.ktorkit:runtime, resolved from here
        mavenCentral()
    }
}
```

KtorKIT is not on Maven Central yet, so both blocks need `mavenLocal()`: the first resolves
the plugin, the second resolves the `runtime` artifact the plugin adds for you.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("io.ktorkit") version "0.1.0-SNAPSHOT"
}
```

The Gradle plugin automatically adds the `runtime` dependency and wires the compiler plugin into `kotlinc`.

Your Kotlin version must match the one KtorKIT was built against (see [Versions](#versions)) —
compiler plugins are bound to the compiler's internal APIs.

### 2. Define your API

```kotlin
import io.ktorkit.*
import kotlinx.serialization.Serializable

@Serializable
data class Post(val id: Int, val userId: Int, val title: String, val body: String)

@ContributesAPI
interface PostsApi {

    @GET("posts")
    suspend fun listPosts(): List<Post>

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Post

    @POST("posts")
    suspend fun createPost(@Body post: Post): Post
}
```

### 3. Create and use the client

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

val client = KtorClient(
    baseUrl = "https://jsonplaceholder.typicode.com/",
    httpClient = HttpClient(CIO),
)

val api = client.create<PostsApi>()

val posts = api.listPosts()          // List<Post>
val post = api.getPost(id = 1)       // Post
```

## Annotations Reference

### HTTP Verbs

Applied to functions inside a `@ContributesAPI` interface. Each function must have exactly one verb annotation.

| Annotation   | Description          |
|-------------|----------------------|
| `@GET`      | HTTP GET request     |
| `@POST`     | HTTP POST request    |
| `@PUT`      | HTTP PUT request     |
| `@DELETE`   | HTTP DELETE request  |
| `@PATCH`    | HTTP PATCH request   |
| `@HEAD`     | HTTP HEAD request    |
| `@OPTIONS`  | HTTP OPTIONS request |

```kotlin
@GET("users/{id}")
suspend fun getUser(@Path("id") id: Int): User
```

### Parameters

| Annotation       | Target    | Description                                          |
|-----------------|-----------|------------------------------------------------------|
| `@Path("name")` | Parameter | Replaces `{name}` in the URL path template. Values are percent-encoded. |
| `@Query("name")`| Parameter | Adds a query parameter `?name=value`                  |
| `@QueryMap`     | Parameter | Adds all entries from a `Map<String, Any?>` as query params |
| `@Body`         | Parameter | Sets the request body                                 |
| `@Header("name")`| Parameter| Adds a request header                                |
| `@HeaderMap`    | Parameter | Adds all entries from a `Map<String, Any?>` as headers|
| `@Field("name")`| Parameter | Adds a form field (requires `@FormUrlEncoded`)        |
| `@FieldMap`     | Parameter | Adds all entries from a `Map<String, Any?>` as form fields (requires `@FormUrlEncoded`) |
| `@Part("name")` | Parameter | Adds a multipart part (requires `@Multipart`)         |
| `@Part(value = "name", fileName = "f.txt")` | Parameter | Adds a multipart part as a file upload; pair with `ByteArray` |
| `@Url`          | Parameter | Overrides the entire request URL                      |

Null values are dropped for `@Query`, `@QueryMap`, `@Header`, `@HeaderMap`, `@Field`, `@FieldMap`, and `@Part`.

### Class-Level

| Annotation                       | Description                                         |
|----------------------------------|-----------------------------------------------------|
| `@ContributesAPI`                | Marks an interface for implementation generation     |
| `@ContributesAPI(baseUrl = "…")` | Sets a per-API base URL (overrides `KtorClient.baseUrl`) |

### Function-Level

| Annotation                          | Description                                       |
|-------------------------------------|---------------------------------------------------|
| `@Headers("X-Key: value", …)`      | Adds static headers to every call of this function |
| `@FormUrlEncoded`                   | Sends the request body as `application/x-www-form-urlencoded` |
| `@Multipart`                        | Sends the request body as `multipart/form-data`    |

## Request Bodies

`@Body` behaves differently depending on the parameter type:

| Parameter type          | Behavior                                                              |
|------------------------|-----------------------------------------------------------------------|
| `String`               | Sent verbatim; content type left to Ktor's default                     |
| Any `@Serializable` `T`| Encoded to JSON at compile time; `Content-Type: application/json` set   |
| `List<T>`, `Map<K, V>` | Encoded to a JSON array / object                                       |
| `T?`                   | Encoded as JSON, `null` becomes the literal `null`                     |

```kotlin
@ContributesAPI
interface ItemsApi {
    // Sent as {"name":"widget","qty":3} with application/json
    @POST("items")
    suspend fun create(@Body item: CreateItem): Item

    // Sent verbatim as text
    @POST("raw")
    suspend fun raw(@Body payload: String): String
}
```

Serialization happens in the compiler, using the same `kotlinx.serialization` machinery as return types — no `ContentNegotiation` plugin is required on the `HttpClient`.

## File Uploads

Annotate the function `@Multipart` and each part `@Part`. A part with a `fileName` is
sent as a file upload; pair it with a `ByteArray`. Everything else is sent as a text part
using its `toString()`.

```kotlin
@ContributesAPI
interface UploadApi {
    @Multipart
    @POST("upload")
    suspend fun upload(
        @Part("meta") meta: String,
        @Part(value = "file", fileName = "notes.txt") file: ByteArray,
    ): UploadResult
}
```

`Content-Type: multipart/form-data` and the boundary are set for you. Null parts are dropped.

## Streaming

Return `Flow<T>` to consume a response incrementally instead of buffering it. The body is
read one line at a time — the newline-delimited JSON (NDJSON) shape used by most streaming
APIs — and each non-blank line is decoded as `T`.

```kotlin
@ContributesAPI
interface EventsApi {
    @GET("stream/{n}")
    fun records(@Path("n") n: Int): Flow<Record>   // note: not suspend

    @GET("logs")
    fun logLines(): Flow<String>                   // raw lines, undecoded
}
```

The request is issued lazily when collection starts, and the connection stays open for the
lifetime of the collection. Collecting the same `Flow` twice issues two requests.

`Flow<String>` emits raw lines verbatim; `Flow<T>` for any other `T` decodes each line and
skips blank ones.

## Return Types

Functions can return any of the following:

| Return Type         | Behavior                                                        |
|--------------------|-----------------------------------------------------------------|
| `String`           | Returns the raw response body as a string                        |
| `T`                | Deserializes the response body as `T` using kotlinx.serialization |
| `T?`               | Deserializes, accepting a JSON `null`                            |
| `List<T>`          | Deserializes a JSON array                                        |
| `Map<K, V>`        | Deserializes a JSON object as a map                              |
| `Response<String>` | Wraps the raw body with HTTP status and headers                  |
| `Response<T>`      | Wraps a deserialized body with HTTP status and headers           |
| `Flow<String>`     | Streams the body as raw lines                                    |
| `Flow<T>`          | Streams the body, decoding one JSON value per line               |

Generic types nest, so `List<Map<String, Int>>` and `Response<List<Post>>` both work.

Functions returning `Flow<T>` are declared without `suspend`; every other return type
requires `suspend`.

The `Response<T>` wrapper gives access to:
```kotlin
response.status     // HttpStatusCode
response.headers    // Headers
response.body       // T
response.isSuccess  // true if status is 2xx
```

## Full Example

```kotlin
@Serializable
data class HttpBinResponse(
    val url: String,
    val method: String,
    val args: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val data: String = "",
    val form: Map<String, String> = emptyMap(),
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

    @Headers("X-Static-A: alpha")
    @GET("anything/withheaders")
    suspend fun withHeaders(
        @Header("X-Token") token: String,
        @HeaderMap extra: Map<String, Any?>,
        @QueryMap params: Map<String, Any?>,
    ): HttpBinResponse

    @POST("anything")
    suspend fun postJson(@Body payload: CreateItem): HttpBinResponse

    @FormUrlEncoded
    @POST("anything")
    suspend fun submitForm(
        @Field("name") name: String,
        @FieldMap extra: Map<String, Any?>,
    ): HttpBinResponse

    @GET("status/{code}")
    suspend fun statusEndpoint(@Path("code") code: Int): Response<String>
}

fun main() = runBlocking {
    val client = KtorClient(
        baseUrl = "https://httpbin.org/",
        httpClient = HttpClient(CIO),
    )
    val api = client.create<HttpBinApi>()

    val result = api.typedAnything(slug = "hello", q = "world")
    println(result.args["q"]) // "world"

    val posted = api.postJson(CreateItem(name = "widget", qty = 3))
    println(posted.data) // {"name":"widget","qty":3}

    val form = api.submitForm(name = "ada", extra = mapOf("role" to "admin"))
    println(form.form) // {name=ada, role=admin}

    val status = api.statusEndpoint(code = 404)
    println(status.isSuccess) // false
    println(status.status.value) // 404
}
```

## Compile-Time Diagnostics

The compiler plugin validates your API definitions and reports errors at compile time:

| Error | Cause |
|-------|-------|
| `@ContributesAPI may only be applied to an interface` | Applied to a class, object, or abstract class |
| `must declare exactly one HTTP verb annotation` | Function has no `@GET`/`@POST`/etc. |
| `declares more than one HTTP verb annotation` | Function has multiple verb annotations |
| `@Path("x") has no matching {x} placeholder` | `@Path` name doesn't match any `{placeholder}` in the URL |
| `URL template contains {x} but no @Path("x")` | URL has a `{placeholder}` with no corresponding `@Path` parameter |
| `@Field/@FieldMap requires the function to be annotated @FormUrlEncoded` | Form parameters would otherwise be silently dropped |
| `@Part requires the function to be annotated @Multipart` | Multipart parts would otherwise be silently dropped |
| `Function declares both @FormUrlEncoded and @Multipart` | Two conflicting body encodings on one function |
| `@Body cannot be combined with @FormUrlEncoded or @Multipart` | The encoded form wins, so the body would be silently dropped |
| `cannot build a serializer for 'X'` | A return type or `@Body` type is not `@Serializable` |

## Under the Hood

KtorKIT is a K2 compiler plugin. It never generates source files, never runs an annotation
processor, and uses no reflection at runtime — the implementation is materialized inside the
compiler itself, across two phases.

```
        ┌──────────────── FRONTEND (FIR) ────────────────┐   ┌──── BACKEND (IR) ────┐

 your      checkers            declaration generation         body filling      call rewriting
 code   ─────────────▶  ────────────────────────────▶  ───────────────────▶  ─────────────────▶  .class
          validate         declare PostsApi.Impl            fill in the         create<T>()
        annotations        (signatures, no bodies)          method bodies       → Impl(client)
```

**Why two phases?** FIR is where the compiler decides what *exists*. The `Impl` class must be
declared there so that the rest of the frontend — and your own code calling `create<PostsApi>()`
— sees a real type with real members and type-checks against it. But FIR declaration generation
can only describe signatures, not bodies. So IR, which runs after type-checking, supplies the
actual code.

### Phase 1 — FIR: declaring the implementation

`KtorKitDeclarationGenerationExtension` registers a `LookupPredicate` for
`@ContributesAPI` and answers three questions the compiler asks it:

| Compiler asks | Plugin answers |
|---|---|
| `getNestedClassifiersNames` | "this interface has a nested class named `Impl`" |
| `generateNestedClassLikeDeclaration` | a `final class Impl : PostsApi` |
| `getCallableNamesForClass` → `generateConstructors` / `generateFunctions` | a constructor taking `KtorClient`, plus one `override` per abstract interface function |

Each generated function copies the interface function's name, value parameters, return type,
and `suspend` modifier. Given this input:

```kotlin
@ContributesAPI
interface PostsApi {
    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Post
}
```

FIR produces the equivalent of:

```kotlin
interface PostsApi {
    class Impl(client: KtorClient) : PostsApi {
        override suspend fun getPost(id: Int): Post   // declared, still bodiless
    }
}
```

Everything generated is tagged with `KtorKitPluginKey`, a `GeneratedDeclarationKey`. That tag is
how the IR phase later recognizes its own declarations — it never guesses from names.

If you declare your own nested `Impl`, the plugin backs off entirely and generates nothing.

Running alongside is `KtorKitFirCheckersExtension`, which contributes the
[diagnostics](#compile-time-diagnostics). Validation happens here, in the frontend, which is why
a mismatched `{placeholder}` is a red squiggle rather than a runtime surprise.

### Phase 2 — IR: filling in the bodies

`GeneratedBodyFiller` walks the IR tree and processes any class whose origin is
`GeneratedByPlugin(KtorKitPluginKey)`. For each one it adds a private `client` field, writes the
constructor body, then writes each method body as a flat call chain against the runtime's
`RequestBuilder`. The example above becomes:

```kotlin
class Impl(client: KtorClient) : PostsApi {
    private val client: KtorClient = client

    override suspend fun getPost(id: Int): Post {
        val rb = client.beginRequest("GET", "posts/{id}")
        rb.path("id", id)
        return rb.executeWithDeserializer(Post.serializer())
    }
}
```

Bindings are emitted in a fixed order: base-URL override, static `@Headers`, the
`@FormUrlEncoded`/`@Multipart` marker, then one call per annotated parameter, then the terminal
execute call.

You can confirm this on your own build — nothing here is hand-waving:

```bash
./gradlew :sample:compileKotlin
javap -p -c sample/build/classes/kotlin/main/sample/HttpBinApi\$Impl.class
```

The disassembly shows the `client` field, the constructor storing it, and each method opening
with `ExecuteKt.beginRequest` and closing with one of the `RequestBuilder.execute*` calls.

**A subtlety worth knowing:** the generated `Impl.getPost` override carries no annotations —
`@GET` and `@Path` live on the *interface* function. So every annotation lookup falls back
through `overriddenSymbols` to find them. This is why `httpMethodOf`, `isFormEncoded`,
`isMultipart`, and `paramAnnotation` all search the overridden declaration rather than the
function in hand.

### Choosing the terminal call

The return type alone decides how the response is consumed:

| Return type | Emitted call |
|---|---|
| `String` | `executeAsString()` |
| `Response<String>` | `executeAsResponseString()` |
| `Response<T>` | `executeAsResponseWithDeserializer(ser)` |
| `Flow<String>` | `executeAsFlowOfString()` |
| `Flow<T>` | `executeAsFlow(ser)` |
| anything else | `executeWithDeserializer(ser)` |

### Resolving serializers at compile time

`resolveSerializer` builds a `KSerializer` expression by recursing on the type, which is what
lets nested generics work and what keeps the whole thing reflection-free:

| Type | Emitted expression |
|---|---|
| `T?` | `resolve(T).nullable` |
| `List<T>` | `ListSerializer(resolve(T))` |
| `Map<K, V>` | `MapSerializer(resolve(K), resolve(V))` |
| `Int`, `String`, … | `Int.serializer()` — the `kotlinx.serialization.builtins` extension |
| any `@Serializable` | `T.Companion.serializer()` |

So `Response<List<Map<String, Int>>>` resolves by peeling one layer at a time down to
primitives. Because the serializer is chosen during compilation, this works identically on
Kotlin/Native and Kotlin/JS, where runtime reflection isn't available.

When a type bottoms out with no serializer, the plugin reports a normal compiler error through
the `MessageCollector` and returns a `kotlin.error(…)` call as a placeholder. Since `error`
returns `Nothing`, that stub type-checks wherever a value was expected, so the build fails on the
readable diagnostic instead of an internal exception with a stack trace.

### Rewriting `create<T>()`

The runtime declaration is deliberately a trap:

```kotlin
inline fun <reified T : Any> KtorClient.create(): T =
    error("KtorKit compiler plugin not applied — call site of create<${T::class.simpleName}>() was not rewritten")
```

That body only ever runs if the plugin is missing, and says so. Normally `CreateCallTransformer`
finds every call to `io.ktorkit.create`, reads the reified type argument, locates the nested
`Impl`, and replaces the entire call with a direct constructor invocation:

```kotlin
val api = client.create<PostsApi>()   // you write this
val api = PostsApi.Impl(client)       // the compiler emits this
```

No service lookup, no proxy, no reflection — just a constructor call.

### Why there is a runtime module at all

Emitting IR is verbose, so the plugin emits as little of it as possible. All the actual HTTP
work — URL assembly, percent-encoding, null-dropping, multipart assembly, line-by-line
streaming — lives in ordinary Kotlin inside `RequestBuilder`. The plugin only emits a linear
sequence of calls into it. That keeps the IR generator small enough to reason about, and means
most behavior can be tested as plain library code.

### How it gets loaded

`compiler` declares `KtorKitCompilerPluginRegistrar` in
`META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`, which is how
`kotlinc` discovers it. The registrar sets `supportsK2 = true`, registers both extensions, and
passes the build's `MessageCollector` down to the IR phase for diagnostics.

The `gradle-plugin` module is a `KotlinCompilerPluginSupportPlugin`: it adds
`io.ktorkit:runtime` to your dependencies and tells the Kotlin Gradle Plugin to put
`io.ktorkit:compiler` on the compiler classpath. Both coordinates use a `KTORKIT_VERSION`
constant generated at build time from the `ktorkitVersion` property, so the version is declared
exactly once.

## Building

```bash
./gradlew build
```

Run the sample (makes live requests to httpbin.org and jsonplaceholder.typicode.com):
```bash
./gradlew :sample:run
```

Run tests:
```bash
./gradlew :sample:test        # JVM tests + compiler diagnostic tests
./gradlew :tests-mp:allTests  # Multiplatform tests (JVM + JS + macOS native)
```

## Requirements

- Kotlin 2.4.10 (K2) — must match exactly; see [Versions](#versions)
- JDK 21+
- Gradle 9.6.1+ (wrapper included)

## Testing

The suite runs on every supported platform:

| Suite | Count | Targets |
|-------|-------|---------|
| `:sample:test` | 131 | JVM — functional tests plus compiler-diagnostic tests driven through `kotlin-compile-testing` |
| `:tests-mp:allTests` | 102 × 3 | JVM, JS (Node), macOS native — the same suite compiled and run per target |

That is 437 test executions in total, all green.

The diagnostic tests are worth a note: they run the *real* compiler in-process via
`kotlin-compile-testing`, feeding it a source snippet and asserting on the exit code and the
emitted message. That is how every error in the table above is verified — including the
negative cases, where a well-formed API must still compile cleanly.

`:sample:run` additionally exercises every feature end-to-end against live
`httpbin.org` and `jsonplaceholder.typicode.com` endpoints.

## Not Yet Supported

- Server-sent events (`text/event-stream`) as a first-class return type — `Flow<String>` can consume the raw lines
- Custom converters or interceptor pipelines beyond what the Ktor `HttpClient` provides
- Per-call timeout or retry annotations — configure these on the `HttpClient` instead

## License

[MIT](LICENSE) © Adkhambek
