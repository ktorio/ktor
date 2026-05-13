// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

// Simple resources for articles
@Resource("/articles")
@Serializable
class Articles {
    @Resource("/{id}")
    @Serializable
    class Id(val parent: Articles, val id: Int) {
        @Resource("/comments")
        class Comments(val parent: Id)
    }

    @Resource("/featured")
    @Serializable
    class Featured(val parent: Articles)
}

// Resources with query parameters
@Resource("/users")
@Serializable
class Users {
    @Resource("/search")
    @Serializable
    class Search(
        val parent: Users,
        val query: String,
        val limit: Int = 10,
        val offset: Int = 0
    )
}

// Resources with body content
@Resource("/posts")
@Serializable
class Posts {
    @Resource("/{id}")
    @Serializable
    class Id(val parent: Posts, val id: Int) {
        @Resource("/comments")
        class Comments(val parent: Id)
    }
}

@Serializable
data class Post(val id: Int, val title: String, val content: String, val authorId: Int)

@Serializable
data class Comment(val text: String, val authorId: Int)

private val posts = mutableListOf(
    Post(1, "Post 1", "Content 1", 1),
    Post(2, "Post 2", "Content 2", 2),
    Post(3, "Post 3", "Content 3", 3)
)

private val comments = mutableListOf(
    Comment("Comment 1.1", 1),
    Comment("Comment 1.2", 1),
    Comment("Comment 2.1", 2)
)

/**
 * This example demonstrates how type-safe routing with @Resource annotations
 * can be used in Ktor applications and processed by the OpenAPI generator.
 */
fun Application.installResources() {
    install(ContentNegotiation) {
        json()
    }

    install(Resources)

    routing {
        /**
         * Get a list of articles.
         */
        get<Articles> { articles ->
            call.respond(posts)
        }

        /**
         * Get a single article by ID.
         */
        get<Articles.Id> { article ->
            val post = posts.find { it.id == article.id }
                ?: return@get call.respondText("Article #${article.id} not found", status = HttpStatusCode.NotFound)
            call.respond(post)
        }

        /**
         * Get a list of comments for a specific article.
         */
        get<Articles.Id.Comments> { comments ->
            call.respond(comments)
        }

        /**
         * Get a list of featured articles.
         */
        get<Articles.Featured> { featured ->
            call.respond(posts)
        }

        /**
         * Search for users.
         */
        get<Users.Search> { search ->
            call.respondText("Searching for users matching '${search.query}', limit: ${search.limit}, offset: ${search.offset}")
        }

        /**
         * Create or update a post.
         */
        post<Posts> {
            val post = call.receive<Post>()
            call.respondText("Created post: ${post.title}")
        }

        /**
         * Update a post.
         */
        put<Posts.Id> { postId ->
            val updatedPost = call.receive<Post>()
            call.respondText("Updated post ${postId.id}: ${updatedPost.title}")
        }

        /**
         * Add a comment to a post.
         */
        post<Posts.Id.Comments> { comments ->
            call.respondText("Added comment to post ${comments.parent.id}")
        }
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, integerLiteral,
nestedClass, primaryConstructor, propertyDeclaration, stringLiteral */
