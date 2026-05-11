// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.TypeInfo
import kotlinx.serialization.Serializable

fun Application.installRouteFunctions() {
    val userRepository = Repository1<User1>()
    val messageRepository = Repository1<Message1>()

    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            userEndpoints(userRepository)
            messageEndpoints(messageRepository)
            get("/summary") {
                withLogging("INFO-summary") { call ->
                    call.respondText("OK", ContentType.Text.Plain)
                }
            }
        }
    }
}

private fun Route.userEndpoints(repository: Repository1<User1>) {
    route("/users") {
        userReadEndpoints(repository)
        userModificationEndpoints(repository)
    }
}

private fun Route.messageEndpoints(repository: Repository1<Message1>) {
    route("/messages") {
        messageReadEndpoints(repository)
        messageModificationEndpoints(repository)
    }
}

private fun Route.userModificationEndpoints(repository: Repository1<User1>) {
    /**
     * Save a new user.
     *
     * @body [User1] The user to save.
     */
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Delete a user.
     * @path id The ID of the user
     */
    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.userReadEndpoints(repository: Repository1<User1>) {
    /**
     * Get a list of users.
     *
     * @response 200 A list of users.
     */
    get {
        val query = call.request.queryParameters.toMap()
        val list = repository.list(query)
        call.respond(list)
    }

    /**
     * Get a single user
     *
     * @path id The ID of the user
     * @response 404 The user was not found
     */
    get("{id}") {
        val user = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(user)
    }
}


private fun Route.messageModificationEndpoints(repository: Repository1<Message1>) {
    /**
     * Save a new message.
     *
     * @body [Message1] The message to save.
     */
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Delete a message.
     * @path id The ID of the message
     */
    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.messageReadEndpoints(repository: Repository1<Message1>) {
    /**
     * Get a list of messages.
     *
     * @response 200 A list of messages.
     */
    get {
        val query = call.request.queryParameters.toMap()
        val list = repository.list(query)
        call.respond(list)
    }

    /**
     * Get a single message
     *
     * @path id The ID of the message
     * @response 404 The message was not found
     */
    get("{id}") {
        val message = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(message)
    }
}

suspend fun RoutingContext.withLogging(
    logPrefix: String,
    block: suspend (ApplicationCall) -> Unit
) {
    val loggingCall = object : ApplicationCall by call {
        override suspend fun respond(message: Any?, typeInfo: TypeInfo?) {
            println("${System.currentTimeMillis()} $logPrefix respond $message")
            call.respond(message, typeInfo)
        }
    }
    block(loggingCall)
}

class Repository1<E> {
    fun get(id: String): E? = null
    fun save(entity: E) {}
    fun delete(id: String) {}
    fun list(query: Map<String, List<String>>): List<E> = emptyList()
}

@Serializable
data class User1(val id: String, val name: String)

@Serializable
data class Message1(val id: String, val text: String)

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, interfaceDeclaration,
nullableType, primaryConstructor, propertyDeclaration, typeParameter */
