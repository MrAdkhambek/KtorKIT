package sample.test

import com.adkhambek.ktor.kit.Body
import com.adkhambek.ktor.kit.ContributesAPI
import com.adkhambek.ktor.kit.DELETE
import com.adkhambek.ktor.kit.Field
import com.adkhambek.ktor.kit.FieldMap
import com.adkhambek.ktor.kit.FormUrlEncoded
import com.adkhambek.ktor.kit.GET
import com.adkhambek.ktor.kit.HEAD
import com.adkhambek.ktor.kit.Header
import com.adkhambek.ktor.kit.HeaderMap
import com.adkhambek.ktor.kit.Headers
import com.adkhambek.ktor.kit.Multipart
import com.adkhambek.ktor.kit.OPTIONS
import com.adkhambek.ktor.kit.Part
import com.adkhambek.ktor.kit.PATCH
import com.adkhambek.ktor.kit.POST
import com.adkhambek.ktor.kit.PUT
import com.adkhambek.ktor.kit.Path
import com.adkhambek.ktor.kit.Query
import com.adkhambek.ktor.kit.QueryMap
import com.adkhambek.ktor.kit.Response
import com.adkhambek.ktor.kit.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Post(val id: Int, val title: String)

@ContributesAPI
interface VerbsApi {
    @GET("res") suspend fun get(): String
    @POST("res") suspend fun post(): String
    @PUT("res") suspend fun put(): String
    @DELETE("res") suspend fun delete(): String
    @PATCH("res") suspend fun patch(): String
    @HEAD("res") suspend fun head(): String
    @OPTIONS("res") suspend fun options(): String
}

@ContributesAPI
interface PathApi {
    @GET("posts/{id}") suspend fun byId(@Path("id") id: Int): String
    @GET("users/{userId}/posts/{postId}") suspend fun nested(
        @Path("userId") u: Int,
        @Path("postId") p: Int,
    ): String
    @GET("items/{name}") suspend fun string(@Path("name") name: String): String
}

@ContributesAPI
interface QueryApi {
    @GET("search") suspend fun single(@Query("q") q: String): String
    @GET("search") suspend fun multi(
        @Query("q") q: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): String
    @GET("search") suspend fun nullable(@Query("q") q: String?): String
    @GET("search") suspend fun map(@QueryMap m: Map<String, Any?>): String
    @GET("{kind}/search") suspend fun pathAndQuery(
        @Path("kind") k: String,
        @Query("q") q: String,
    ): String
}

@ContributesAPI
interface HeaderApi {
    @GET("res") suspend fun single(@Header("X-Token") t: String): String
    @GET("res") suspend fun nullable(@Header("X-Token") t: String?): String
    @Headers("X-A: alpha", "X-B: beta") @GET("res") suspend fun staticHeaders(): String
    @GET("res") suspend fun headerMap(@HeaderMap m: Map<String, Any?>): String
    @Headers("X-Static: s") @GET("res") suspend fun mixed(
        @Header("X-Dyn") dyn: String,
        @HeaderMap extra: Map<String, Any?>,
    ): String
}

@ContributesAPI
interface BodyApi {
    @POST("res") suspend fun stringBody(@Body s: String): String
    @POST("res") suspend fun objectBody(@Body p: Post): String
    @POST("res") suspend fun listBody(@Body ps: List<Post>): String
    @POST("res") suspend fun mapBody(@Body m: Map<String, String>): String
    @POST("res") suspend fun nullableBody(@Body p: Post?): String
    @PUT("res") suspend fun putBody(@Body p: Post): String
    @DELETE("res") suspend fun deleteBody(@Body p: Post): String
}

@ContributesAPI
interface FormApi {
    @FormUrlEncoded @POST("res") suspend fun simple(
        @Field("name") n: String,
        @Field("age") age: Int,
    ): String
    @FormUrlEncoded @POST("res") suspend fun nullableField(
        @Field("name") n: String,
        @Field("nickname") nick: String?,
    ): String
    @FormUrlEncoded @POST("res") suspend fun fieldMap(
        @FieldMap m: Map<String, Any?>,
    ): String
    @FormUrlEncoded @POST("res") suspend fun fieldAndMap(
        @Field("name") n: String,
        @FieldMap extra: Map<String, Any?>,
    ): String
}

@ContributesAPI
interface MultipartApi {
    @Multipart @POST("upload") suspend fun textParts(
        @Part("name") name: String,
        @Part("age") age: Int,
    ): String
    @Multipart @POST("upload") suspend fun filePart(
        @Part("meta") meta: String,
        @Part(value = "file", fileName = "report.txt") file: ByteArray,
    ): String
    @Multipart @POST("upload") suspend fun nullablePart(
        @Part("name") name: String,
        @Part("optional") optional: String?,
    ): String
}

@ContributesAPI
interface StreamApi {
    @GET("stream") fun posts(): Flow<Post>
    @GET("stream") fun lines(): Flow<String>
    @GET("stream") fun maps(): Flow<Map<String, Int>>
}

@ContributesAPI
interface UnitApi {
    @DELETE("posts/{id}") suspend fun delete(@Path("id") id: Int)
    @POST("ping") suspend fun ping()
}

@ContributesAPI
interface ReturnApi {
    @GET("text") suspend fun string(): String
    @GET("post") suspend fun typed(): Post
    @GET("posts") suspend fun list(): List<Post>
    @GET("map") suspend fun map(): Map<String, String>
    @GET("nullable") suspend fun nullableTyped(): Post?
    @GET("listMap") suspend fun listOfMaps(): List<Map<String, Int>>
    @GET("response/string") suspend fun responseString(): Response<String>
    @GET("response/typed") suspend fun responseTyped(): Response<Post>
    @GET("response/list") suspend fun responseList(): Response<List<Post>>
}

@ContributesAPI
interface UrlApi {
    @GET("posts/1") suspend fun normal(): String
    @GET("") suspend fun absolute(@Url url: String): String
    @GET("") suspend fun absoluteTyped(@Url url: String): Post
}

@ContributesAPI(baseUrl = "https://api.declared.example/")
interface DeclaredBaseApi {
    @GET("posts/{id}") suspend fun get(@Path("id") id: Int): String
}

@ContributesAPI(baseUrl = "https://api.declared.example/")
interface DeclaredBaseListApi {
    @GET("posts") suspend fun list(): List<Post>
}
