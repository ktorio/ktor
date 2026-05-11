// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.util.toMap
import kotlinx.serialization.Serializable

fun Application.installNesting() {
    val repository = Repository0<User0>()

    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {

            /**
             * @tag admin
             */
            route("/users") {

                /**
                 * Get a list of users.
                 *
                 * @query q [String] Search query
                 * @query limit [Int] Max items to return
                 *   minimum: 1
                 *   maximum: 100
                 * @response 200 [User0]+ A list of users.
                 */
                get {
                    val query = call.request.queryParameters.toMap()
                    val list = repository.list(query)
                    call.respond(list)
                }

                /**
                 * Get a single user
                 *
                 * @path id [Int] The ID of the user
                 * @response 400 Bad ID argument
                 * @response 404 The user was not found
                 */
                get("{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val user = repository.get(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(user)
                }

                /**
                 * Save a new user.
                 *
                 * @body [User0] the user to save.
                 */
                post {
                    repository.save(call.receive())
                    call.respond(HttpStatusCode.Created)
                }

                /**
                 * Delete a user.
                 * @tag danger
                 * @path id [Int] The ID of the user
                 * @response 400 Bad ID argument
                 * @response 204 The user was deleted
                 */
                delete("{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    repository.delete(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

class Repository0<E> {
    fun get(id: Int): E? = null
    fun save(entity: E) {}
    fun delete(id: Int) {}
    fun list(query: Map<String, List<String>>): List<E> = emptyList()
}

@Serializable
data class User0(val id: Int, val name: String)

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, interfaceDeclaration,
nullableType, primaryConstructor, propertyDeclaration, typeParameter */
