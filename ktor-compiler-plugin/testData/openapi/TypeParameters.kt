// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.util.toMap
import kotlinx.serialization.Serializable

fun Application.installTypeParameters() {
    val userRepository = Repository2<User2>()
    val messageRepository = Repository2<Message2>()

    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            crudEndpoints("users", userRepository)
            crudEndpoints("messages", messageRepository)
        }
    }
}

private inline fun <reified E> Route.crudEndpoints(path: String, repository: Repository2<E>) {
    route("data/$path") {
        readEndpoints(repository)
        modificationEndpoints(repository)
    }
}


private inline fun <reified E> Route.readEndpoints(repository: Repository2<E>) {
    get {
        val query = call.request.queryParameters.toMap()
        val list = repository.list(query)
        call.respond(list)
    }
    get("{id}") {
        val item = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(item)
    }
}

private inline fun <reified E> Route.modificationEndpoints(repository: Repository2<E>) {
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

class Repository2<E> {
    fun get(id: String): E? = null
    fun save(entity: E) = Unit
    fun delete(id: String) = Unit
    fun list(query: Map<String, List<String>>): List<E> = emptyList()
}

@Serializable
data class User2(val id: String, val name: String)

@Serializable
data class Message2(val id: String, val text: String)

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, interfaceDeclaration,
nullableType, primaryConstructor, propertyDeclaration, typeParameter */
