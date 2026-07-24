import com.adkhambek.ktor.kit.ContributesAPI
import com.adkhambek.ktor.kit.GET
import com.adkhambek.ktor.kit.Path
import kotlinx.serialization.Serializable

@Serializable
data class Post(val id: Int, val title: String)

@ContributesAPI
interface PostsApi {
    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Post
}
