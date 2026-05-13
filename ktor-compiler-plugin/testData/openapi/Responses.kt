// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.writeInt
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
fun Application.installResponses() {
    val fs: FileSystem = SystemFileSystem

    install(ContentNegotiation) {
        jsonIo()
    }

    routing {
        get("/json") {
            call.respondText("\"Hello, world!\"", ContentType.Application.Json)
        }
        get("/binary") {
            call.response.headers.append("X-Question", "life, the universe, and everything")
            call.respondBytesWriter {
                writeInt(42)
            }
        }
        get("/file") {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"foo.zip\"")
            call.respondFile(File("foo.zip"))
        }
        get("/audio") {
            call.respondSource(fs.source(Path("foo.mp3")), contentType = ContentType.Audio.MPEG)
        }

        route("/api") {
            /**
             * Get list of dudes
             * @response 200 [Dude]+ List of dudes
             */
            get("/dudes") {
                call.respond(
                    listOf(
                        Dude(1, "John"),
                        Dude(2, "Denis")
                    )
                )
            }

            /**
             * Get a specific dude
             * @path id [Int] Dude ID
             * @response 200 [Dude] Dude details
             * @response 404 Dude not found
             */
            get("/dudes/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")

                if (id == 1) {
                    call.respond(Dude(1, "John"))
                } else if (id == 2) {
                    call.respond(Dude(2, "Denis"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Dude not found")
                }
            }

            /**
             * Create a new dude
             * @body [DudeRequest] Dude creation request
             * @response 201 [Dude] Created dude
             */
            post("/dudes") {
                val dudeRequest = try {
                    call.receive<DudeRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ResponseError("Bad Request", e.message ?: "Invalid request body"),
                    )
                }
                val newDude = Dude(3, dudeRequest.name)
                call.respond(HttpStatusCode.Created, newDude)
            }
        }

        route("/status") {
            /**
             * OK response
             * @response 200 OK response
             */
            get("/ok") {
                call.respond(HttpStatusCode.OK, "OK")
            }

            /**
             * Created response
             * @response 201 Resource created
             * @header Location URI of created resource
             */
            get("/created") {
                call.response.header(HttpHeaders.Location, "/api/resources/123")
                call.respond(HttpStatusCode.Created, "Created")
            }

            /**
             * Accepted response
             * @response 202 Request accepted for processing
             */
            get("/accepted") {
                call.respond(HttpStatusCode.Accepted, "Accepted")
            }

            /**
             * No Content response
             * @response 204 Operation successful, no content returned
             */
            get("/no-content") {
                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * Bad Request response
             * @response 400 [ResponseError] Bad request
             */
            get("/bad-request") {
                call.respond(HttpStatusCode.BadRequest, ResponseError("BAD_REQUEST", "Invalid parameters"))
            }

            /**
             * Unauthorized response
             * @response 401 [ResponseError] Authentication required
             */
            get("/unauthorized") {
                call.respond(HttpStatusCode.Unauthorized, ResponseError("UNAUTHORIZED", "Authentication required"))
            }

            /**
             * Forbidden response
             * @response 403 [ResponseError] Permission denied
             */
            get("/forbidden") {
                call.respond(HttpStatusCode.Forbidden, ResponseError("FORBIDDEN", "Insufficient permissions"))
            }

            /**
             * Not Found response
             * @response 404 [ResponseError] Resource not found
             */
            get("/not-found") {
                call.respond(HttpStatusCode.NotFound, ResponseError("NOT_FOUND", "Resource not found"))
            }

            /**
             * Method Not Allowed response
             * @response 405 [ResponseError] Method not allowed
             */
            get("/method-not-allowed") {
                call.respond(HttpStatusCode.MethodNotAllowed, ResponseError("METHOD_NOT_ALLOWED", "Method not allowed"))
            }

            /**
             * Internal Server Error response
             * @response 500 [ResponseError] Server error
             */
            get("/server-error") {
                call.respond(HttpStatusCode.InternalServerError, ResponseError("SERVER_ERROR", "Internal server error"))
            }
        }

        route("/content-types") {
            /**
             * Plain text response
             * @response 200 Plain text
             * @header Content-Type text/plain
             */
            get("/text") {
                call.respondText("Hello, world!", ContentType.Text.Plain)
            }

            /**
             * HTML response
             * @response 200 HTML content
             * @header Content-Type text/html
             */
            get("/html") {
                call.respondText("<html><body><h1>Hello, world!</h1></body></html>", ContentType.Text.Html)
            }

            /**
             * XML response
             * @response 200 XML content
             * @header Content-Type application/xml
             */
            get("/xml") {
                call.respondText("<root><message>Hello, world!</message></root>", ContentType.Application.Xml)
            }

            /**
             * CSV response
             * @response 200 CSV content
             * @header Content-Type text/csv
             */
            get("/csv") {
                call.respondText("id,name\n1,John\n2,Denis", ContentType.Text.CSV)
            }
        }

        route("/complex") {

            /**
             * Parameterized type responses
             * @response 200 An ApiResponse parameterized with DudeDetails
             */
            get("/parameterized") {
                val dudeDetails = DudeDetails(
                    dude = Dude(1, "John"),
                    stats = Stats(
                        posts = 42,
                        followers = 100,
                        following = 50
                    ),
                    metadata = mapOf(
                        "joinDate" to "2023-01-01",
                        "role" to "admin"
                    )
                )

                call.respond(
                    ApiResponse(
                        data = dudeDetails,
                        status = "success",
                        timestamp = "2023-05-12T15:30:45Z"
                    )
                )
            }

            /**
             * Map response
             * @response 200 :[String] Map response
             */
            get("/map") {
                call.respond(
                    mapOf(
                        "name" to "John",
                        "age" to "30",
                        "active" to "true",
                        "tags" to "kotlin,programming"
                    )
                )
            }

            /**
             * Array response
             * @response 200 [String]+ Array response
             */
            get("/array") {
                call.respond(listOf("apple", "banana", "cherry"))
            }
        }

        route("/polymorphic") {
            /**
             * Get animal by ID
             * @path id [Int] Animal ID
             * @response 200 [Animal] Animal details (can be Dog or Cat)
             * @response 404 Animal not found
             */
            get("/animals/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")

                val animal = when (id) {
                    1 -> Dog(id = 1, name = "Rex", breed = "German Shepherd")
                    2 -> Cat(id = 2, name = "Whiskers", lives = 9)
                    else -> null
                }

                if (animal != null) {
                    call.respond(animal)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal not found")
                }
            }

            /**
             * Get list of animals
             * @response 200 [Animal]+ List of animals (mixed Dogs and Cats)
             */
            get("/animals") {
                call.respond(
                    listOf(
                        Dog(id = 1, name = "Rex", breed = "German Shepherd"),
                        Cat(id = 2, name = "Whiskers", lives = 9),
                        Dog(id = 3, name = "Buddy", breed = "Golden Retriever")
                    )
                )
            }
        }

        route("/results") {
            /**
             * Operation with multiple possible outcomes
             * @query success [Boolean] Should the operation succeed
             *   default: true
             * @query includeWarning [Boolean] Include warning in response
             *   default: false
             * @response 200 [SuccessResponse] Operation succeeded
             * @response 207 [PartialSuccessResponse] Operation partially succeeded
             * @response 400 [ResponseError] Operation failed - client error
             * @response 500 [ResponseError] Operation failed - server error
             */
            get("/operation") {
                val success = call.request.queryParameters["success"]?.toBoolean() ?: true
                val includeWarning = call.request.queryParameters["includeWarning"]?.toBoolean() ?: false

                if (success) {
                    if (includeWarning) {
                        call.respond(
                            HttpStatusCode.MultiStatus,
                            PartialSuccessResponse(
                                message = "Operation completed with warnings",
                                warnings = listOf("Some data could not be processed")
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            SuccessResponse(
                                message = "Operation completed successfully",
                                data = mapOf("id" to "123", "status" to "completed")
                            )
                        )
                    }
                } else {
                    val random = (Math.random() * 2).toInt()
                    if (random == 0) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ResponseError(
                                code = "BAD_REQUEST",
                                message = "Invalid parameters"
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ResponseError(
                                code = "SERVER_ERROR",
                                message = "Failed to process request"
                            )
                        )
                    }
                }
            }
        }

        route("/headers") {
            /**
             * Response with custom headers
             * @response 200 OK with headers
             * @header X-Rate-Limit-Limit Maximum number of requests
             * @header X-Rate-Limit-Remaining Remaining requests in the current period
             * @header X-Rate-Limit-Reset Time when the rate limit resets
             */
            get("/rate-limit") {
                call.response.headers.append("X-Rate-Limit-Limit", "100")
                call.response.headers.append("X-Rate-Limit-Remaining", "95")
                call.response.headers.append("X-Rate-Limit-Reset", "1616799120")
                call.respondText("Rate limit info provided in headers")
            }

            /**
             * Response with cache control headers
             * @response 200 OK with cache headers
             * @header Cache-Control Caching directives
             * @header ETag Entity tag for cache validation
             * @header Last-Modified Last modification date
             */
            get("/cache") {
                call.response.headers.append("Cache-Control", "max-age=3600, public")
                call.response.headers.append("ETag", "\"abc123\"")
                call.response.headers.append("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
                call.respondText("Cache info provided in headers")
            }
        }
    }
}

@Serializable
data class Dude(val id: Int, val name: String)

@Serializable
data class DudeRequest(val name: String)

@Serializable
data class ResponseError(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)

@Serializable
data class Stats(
    val posts: Int,
    val followers: Int,
    val following: Int
)

@Serializable
data class DudeDetails(
    val dude: Dude,
    val stats: Stats,
    val metadata: Map<String, String>
)

@Serializable
data class ApiResponse<T>(
    val data: T,
    val status: String,
    val timestamp: String
)

@Serializable
data class SuccessResponse(
    val message: String,
    val data: Map<String, String>
)

@Serializable
data class PartialSuccessResponse(
    val message: String,
    val warnings: List<String>
)

@Serializable
sealed class Animal {
    abstract val id: Int
    abstract val name: String
}

@Serializable
data class Dog(
    override val id: Int,
    override val name: String,
    val breed: String
) : Animal()

@Serializable
data class Cat(
    override val id: Int,
    override val name: String,
    val lives: Int
) : Animal()

/* GENERATED_FIR_TAGS: classDeclaration, classReference, data, funWithExtensionReceiver, functionDeclaration,
primaryConstructor, propertyDeclaration */
