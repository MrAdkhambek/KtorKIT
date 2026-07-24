package sample.test

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.ktorkit.compiler.KtorKitCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
private fun compileBad(@org.intellij.lang.annotations.Language("kotlin") source: String): JvmCompilationResult {
    return KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Bad.kt", source))
        compilerPluginRegistrars = listOf(KtorKitCompilerPluginRegistrar())
        inheritClassPath = true
        messageOutputStream = System.out
        verbose = false
    }.compile()
}

class ContributesApiOnInterfaceCheckerTests {
    @Test fun `@ContributesAPI on a class produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            @ContributesAPI
            class BadOnClass
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "@ContributesAPI may only be applied to an interface" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@ContributesAPI on an object produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            @ContributesAPI
            object BadObject
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("@ContributesAPI may only be applied to an interface" in result.messages)
    }

    @Test fun `@ContributesAPI on an abstract class produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            @ContributesAPI
            abstract class BadAbstract
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("@ContributesAPI may only be applied to an interface" in result.messages)
    }
}

class HttpVerbCheckerTests {
    @Test fun `function with no HTTP verb produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            @ContributesAPI
            interface Api {
                suspend fun missingVerb(): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "must declare exactly one HTTP verb annotation" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `function with two HTTP verbs produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @GET("a")
                @POST("a")
                suspend fun multipleVerbs(): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("declares more than one HTTP verb annotation" in result.messages)
    }

    @Test fun `function in non-ContributesAPI interface is not checked`() {
        // Not annotated -> our checker shouldn't fire even though no verb annotation present
        val result = compileBad(
            """
            interface NotMyApi {
                suspend fun noVerbHere(): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}

class PathPlaceholderCheckerTests {
    @Test fun `extra @Path with no template placeholder produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}")
                suspend fun extra(@Path("id") id: Int, @Path("notInUrl") name: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "@Path(\"notInUrl\") has no matching {notInUrl} placeholder" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `template placeholder with no @Path param produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}/comments/{commentId}")
                suspend fun missing(@Path("id") id: Int): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "URL template contains {commentId} but no @Path(\"commentId\")" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `path with template and matching @Path is OK`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.Path
            @ContributesAPI
            interface Api {
                @GET("posts/{id}")
                suspend fun ok(@Path("id") id: Int): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test fun `multiple placeholders all matched is OK`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.Path
            @ContributesAPI
            interface Api {
                @GET("users/{userId}/posts/{postId}")
                suspend fun ok(@Path("userId") u: Int, @Path("postId") p: Int): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}

class FormEncodingCheckerTests {
    @Test fun `@Field without @FormUrlEncoded produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Field
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun bad(@Field("name") name: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "requires the function to be annotated @FormUrlEncoded" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@FieldMap without @FormUrlEncoded produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.FieldMap
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun bad(@FieldMap m: Map<String, Any?>): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("requires the function to be annotated @FormUrlEncoded" in result.messages)
    }

    @Test fun `@Field with @FormUrlEncoded is OK`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Field
            import io.ktorkit.FieldMap
            import io.ktorkit.FormUrlEncoded
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @POST("res")
                suspend fun ok(@Field("name") name: String, @FieldMap extra: Map<String, Any?>): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test fun `@Body without @FormUrlEncoded is unaffected`() {
        val result = compileBad(
            """
            import io.ktorkit.Body
            import io.ktorkit.ContributesAPI
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun ok(@Body s: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }
}

class MultipartCheckerTests {
    @Test fun `@Part without @Multipart produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.POST
            import io.ktorkit.Part
            @ContributesAPI
            interface Api {
                @POST("upload")
                suspend fun bad(@Part("file") file: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "@Part requires the function to be annotated @Multipart" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@Part with @Multipart is OK`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Multipart
            import io.ktorkit.POST
            import io.ktorkit.Part
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test fun `@FormUrlEncoded plus @Multipart produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.FormUrlEncoded
            import io.ktorkit.Multipart
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @Multipart
                @POST("upload")
                suspend fun bad(): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "pick one body encoding" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@Field inside a @Multipart function produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Field
            import io.ktorkit.Multipart
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @Multipart
                @POST("upload")
                suspend fun bad(@Field("name") name: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("@FormUrlEncoded" in result.messages, "messages were:\n${result.messages}")
    }
}

class BodyEncodingConflictTests {
    @Test fun `@Body on a @Multipart function produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.Body
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Multipart
            import io.ktorkit.POST
            import io.ktorkit.Part
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
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "@Body cannot be combined with" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@Body on a @FormUrlEncoded function produces error`() {
        val result = compileBad(
            """
            import io.ktorkit.Body
            import io.ktorkit.ContributesAPI
            import io.ktorkit.Field
            import io.ktorkit.FormUrlEncoded
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @FormUrlEncoded
                @POST("res")
                suspend fun bad(@Field("n") n: String, @Body meta: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "@Body cannot be combined with" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `@Body alone remains valid`() {
        val result = compileBad(
            """
            import io.ktorkit.Body
            import io.ktorkit.ContributesAPI
            import io.ktorkit.POST
            @ContributesAPI
            interface Api {
                @POST("res")
                suspend fun ok(@Body s: String): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }
}

class StreamingCompilationTests {
    // Note: the kotlinx-serialization plugin is not applied in this harness, so
    // these cases deliberately stick to Flow<String>, which needs no serializer.
    @Test fun `Flow of String return type compiles`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import kotlinx.coroutines.flow.Flow
            @ContributesAPI
            interface Api {
                @GET("stream") fun lines(): Flow<String>
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test fun `non-suspend Flow function is accepted by the verb checker`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.POST
            import kotlinx.coroutines.flow.Flow
            @ContributesAPI
            interface Api {
                @POST("stream") fun lines(): Flow<String>
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }
}

class NonSerializableTypeTests {
    @Test fun `non-Serializable return type reports a readable error`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @GET("x") suspend fun get(): NotSerializable
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "cannot build a serializer for" in result.messages,
            "messages were:\n${result.messages}",
        )
        assertTrue(
            "Annotate the type with @Serializable" in result.messages,
            "messages were:\n${result.messages}",
        )
    }

    @Test fun `non-Serializable type does not surface an internal exception`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET

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
        val result = compileBad(
            """
            import io.ktorkit.Body
            import io.ktorkit.ContributesAPI
            import io.ktorkit.POST

            data class NotSerializable(val id: Int)

            @ContributesAPI
            interface Api {
                @POST("x") suspend fun send(@Body b: NotSerializable): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "cannot build a serializer for" in result.messages,
            "messages were:\n${result.messages}",
        )
    }
}

class ControlGroupTests {
    @Test fun `well-formed @ContributesAPI compiles cleanly`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            import io.ktorkit.Path
            import io.ktorkit.Query
            @ContributesAPI
            interface GoodApi {
                @GET("posts/{id}")
                suspend fun get(@Path("id") id: Int, @Query("q") q: String?): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test fun `interface with declared baseUrl compiles`() {
        val result = compileBad(
            """
            import io.ktorkit.ContributesAPI
            import io.ktorkit.GET
            @ContributesAPI(baseUrl = "https://api.example/")
            interface GoodApi {
                @GET("posts")
                suspend fun list(): String
            }
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }
}
