package sample.test

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.adkhambek.ktor.kit.compiler.KtorKitCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
private fun compile(@org.intellij.lang.annotations.Language("kotlin") source: String): JvmCompilationResult {
    return KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Bad.kt", source))
        compilerPluginRegistrars = listOf(KtorKitCompilerPluginRegistrar())
        inheritClassPath = true
        messageOutputStream = System.out
        verbose = false
    }.compile()
}

/**
 * Asserts the plugin rejected the snippet. Always attaches the compiler output, so a
 * regression reports which diagnostics actually fired instead of a bare enum mismatch.
 */
private fun JvmCompilationResult.assertFailedWith(vararg expectedInMessage: String) {
    assertEquals(
        KotlinCompilation.ExitCode.COMPILATION_ERROR,
        exitCode,
        "expected a compile error. messages were:\n$messages",
    )
    for (expected in expectedInMessage) {
        assertTrue(expected in messages, "expected \"$expected\" in messages, but got:\n$messages")
    }
}

/** Asserts the snippet compiled cleanly, reporting the compiler output when it did not. */
private fun JvmCompilationResult.assertCompiled() {
    assertEquals(KotlinCompilation.ExitCode.OK, exitCode, messages)
}

class ContributesApiOnInterfaceCheckerTests {
    @Test fun `@ContributesAPI on a class produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            @ContributesAPI
            class BadOnClass
            """.trimIndent()
        )
        result.assertFailedWith("@ContributesAPI may only be applied to an interface")
    }

    @Test fun `@ContributesAPI on an object produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            @ContributesAPI
            object BadObject
            """.trimIndent()
        )
        result.assertFailedWith("@ContributesAPI may only be applied to an interface")
    }

    @Test fun `@ContributesAPI on an abstract class produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            @ContributesAPI
            abstract class BadAbstract
            """.trimIndent()
        )
        result.assertFailedWith("@ContributesAPI may only be applied to an interface")
    }
}

class HttpVerbCheckerTests {
    @Test fun `function with no HTTP verb produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            @ContributesAPI
            interface Api {
                suspend fun missingVerb(): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("must declare exactly one HTTP verb annotation")
    }

    @Test fun `function with two HTTP verbs produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @GET("a")
                @POST("a")
                suspend fun multipleVerbs(): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("declares more than one HTTP verb annotation")
    }

    @Test fun `function in non-ContributesAPI interface is not checked`() {
        // Not annotated -> our checker shouldn't fire even though no verb annotation present
        val result = compile(
            """
            interface NotMyApi {
                suspend fun noVerbHere(): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class PathPlaceholderCheckerTests {
    @Test fun `extra @Path with no template placeholder produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}")
                suspend fun extra(@Path("id") id: Int, @Path("notInUrl") name: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("@Path(\"notInUrl\") has no matching {notInUrl} placeholder")
    }

    @Test fun `template placeholder with no @Path param produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}/comments/{commentId}")
                suspend fun missing(@Path("id") id: Int): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("URL template contains {commentId} but no @Path(\"commentId\")")
    }

    @Test fun `path with template and matching @Path is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}")
                suspend fun ok(@Path("id") id: Int): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `multiple placeholders all matched is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            @ContributesAPI
            interface Api {
                @GET("users/{userId}/posts/{postId}")
                suspend fun ok(@Path("userId") u: Int, @Path("postId") p: Int): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class FormEncodingCheckerTests {
    @Test fun `@Field without @FormUrlEncoded produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Field
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun bad(@Field("name") name: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("requires the function to be annotated @FormUrlEncoded")
    }

    @Test fun `@FieldMap without @FormUrlEncoded produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.FieldMap
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun bad(@FieldMap m: Map<String, Any?>): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("requires the function to be annotated @FormUrlEncoded")
    }

    @Test fun `@Field with @FormUrlEncoded is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Field
            import com.adkhambek.ktor.kit.FieldMap
            import com.adkhambek.ktor.kit.FormUrlEncoded
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @POST("res")
                suspend fun ok(@Field("name") name: String, @FieldMap extra: Map<String, Any?>): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `@Body without @FormUrlEncoded is unaffected`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.Body
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun ok(@Body s: String): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class MultipartCheckerTests {
    @Test fun `@Part without @Multipart produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.POST
            import com.adkhambek.ktor.kit.Part
            @ContributesAPI
            interface Api {
                @POST("upload")
                suspend fun bad(@Part("file") file: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("@Part requires the function to be annotated @Multipart")
    }

    @Test fun `@Part with @Multipart is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Multipart
            import com.adkhambek.ktor.kit.POST
            import com.adkhambek.ktor.kit.Part
            @ContributesAPI
            interface Api {
                @Multipart
                @POST("upload")
                suspend fun ok(
                    @Part("meta") meta: String,
                    @Part(value = "file", fileName = "a.txt") file: ByteArray,
                ): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `@FormUrlEncoded plus @Multipart produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.FormUrlEncoded
            import com.adkhambek.ktor.kit.Multipart
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @Multipart
                @POST("upload")
                suspend fun bad(): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("pick one body encoding")
    }

    @Test fun `@Field inside a @Multipart function produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Field
            import com.adkhambek.ktor.kit.Multipart
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @Multipart
                @POST("upload")
                suspend fun bad(@Field("name") name: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("@FormUrlEncoded")
    }
}

class BodyEncodingConflictTests {
    @Test fun `@Body on a @Multipart function produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.Body
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Multipart
            import com.adkhambek.ktor.kit.POST
            import com.adkhambek.ktor.kit.Part
            @ContributesAPI
            interface Api {
                @Multipart
                @POST("upload")
                suspend fun bad(
                    @Part(value = "file", fileName = "a.png") file: ByteArray,
                    @Body meta: String,
                ): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("@Body cannot be combined with")
    }

    @Test fun `@Body on a @FormUrlEncoded function produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.Body
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.Field
            import com.adkhambek.ktor.kit.FormUrlEncoded
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @POST("res")
                suspend fun bad(@Field("n") n: String, @Body meta: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("@Body cannot be combined with")
    }

    @Test fun `@Body alone remains valid`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.Body
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun ok(@Body s: String): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class StreamingCompilationTests {
    // Note: the kotlinx-serialization plugin is not applied in this harness, so
    // these cases deliberately stick to Flow<String>, which needs no serializer.
    @Test fun `Flow of String return type compiles`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import kotlinx.coroutines.flow.Flow
            @ContributesAPI
            interface Api {
                @GET("stream") fun lines(): Flow<String>
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `non-suspend Flow function is accepted by the verb checker`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.POST
            import kotlinx.coroutines.flow.Flow
            @ContributesAPI
            interface Api {
                @POST("stream") fun lines(): Flow<String>
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class NonSerializableTypeTests {
    @Test fun `non-Serializable return type reports a readable error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @GET("x") suspend fun get(): NotSerializable
            }
            """.trimIndent()
        )
        result.assertFailedWith("cannot build a serializer for", "Annotate the type with @Serializable")
    }

    @Test fun `non-Serializable type does not surface an internal exception`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @GET("x") suspend fun get(): NotSerializable
            }
            """.trimIndent()
        )
        assertFalse(
            "IllegalStateException" in result.messages,
            "expected a clean diagnostic, got:\n${result.messages}",
        )
    }

    @Test fun `non-Serializable @Body reports a readable error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.Body
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.POST

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @POST("x") suspend fun send(@Body b: NotSerializable): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("cannot build a serializer for")
    }
}

class ControlGroupTests {
    @Test fun `well-formed @ContributesAPI compiles cleanly`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            import com.adkhambek.ktor.kit.Query
            @ContributesAPI
            interface GoodApi {
                @GET("posts/{id}")
                suspend fun get(@Path("id") id: Int, @Query("q") q: String?): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `interface with declared baseUrl compiles`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            @ContributesAPI(baseUrl = "https://api.example/")
            interface GoodApi {
                @GET("posts")
                suspend fun list(): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class ParameterBindingCheckerTests {
    @Test fun `unannotated parameter produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            @ContributesAPI
            interface Api {
                @GET("search")
                suspend fun search(q: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("Parameter has no KtorKit annotation")
    }

    @Test fun `two binding annotations on one parameter produces error`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Header
            import com.adkhambek.ktor.kit.Query
            @ContributesAPI
            interface Api {
                @GET("search")
                suspend fun search(@Query("q") @Header("X-Q") q: String): String
            }
            """.trimIndent()
        )
        result.assertFailedWith("more than one KtorKit binding annotation")
    }

    @Test fun `every parameter annotated is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            import com.adkhambek.ktor.kit.Path
            import com.adkhambek.ktor.kit.Query
            @ContributesAPI
            interface Api {
                @GET("posts/{id}")
                suspend fun get(@Path("id") id: Int, @Query("q") q: String?): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }

    @Test fun `zero-parameter function is OK`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET
            @ContributesAPI
            interface Api {
                @GET("posts") suspend fun list(): String
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class UnitReturnTests {
    @Test fun `Unit return compiles without a serializer`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.DELETE
            import com.adkhambek.ktor.kit.Path
            @ContributesAPI
            interface Api {
                @DELETE("posts/{id}")
                suspend fun delete(@Path("id") id: Int)
            }
            """.trimIndent()
        )
        result.assertCompiled()
    }
}

class DiagnosticLocationTests {
    @Test fun `serializer error reports the source file`() {
        val result = compile(
            """
            import com.adkhambek.ktor.kit.ContributesAPI
            import com.adkhambek.ktor.kit.GET

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @GET("x") suspend fun get(): NotSerializable
            }
            """.trimIndent()
        )
        result.assertFailedWith("cannot build a serializer for", "Bad.kt")
    }
}
