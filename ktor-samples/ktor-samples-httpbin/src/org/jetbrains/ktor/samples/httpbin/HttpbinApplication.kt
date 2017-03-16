package org.jetbrains.ktor.samples.httpbin

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.CacheControl
import org.jetbrains.ktor.content.CacheControlVisibility
import org.jetbrains.ktor.content.TextContent
import org.jetbrains.ktor.content.resolveClasspathWithPath
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.respondHtml
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.CallLogging
import org.jetbrains.ktor.request.MultiPartData
import org.jetbrains.ktor.request.PartData
import org.jetbrains.ktor.request.header
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.transform.transform
import org.jetbrains.ktor.util.ValuesMap
import org.jetbrains.ktor.util.flattenEntries
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.*

/**
 * Httpbin Ktor Application implementing (large parts of)
 *
 *   httpbin(1) HTTP Request & Response Service https://httpbin.org/
 *
 * ENDPOINTS
 *
 *     /                               HTML page describing the service
 *          TODO: httpbin.1.html needs to be converted to freemarker so that the links work
 *     /postman                        Downloads postman collection for httpbin
 *     /ip                             Returns Origin IP.
 *     /user-agent                     Returns user-agent.
 *     /headers                        Returns header dict.
 *     /get                            Returns GET data.
 *     /post                           Returns POST data.
 *     /forms/post                     HTML form that submits to /post
 *     /patch                          Returns PATCH data.
 *     /put                            Returns PUT data.
 *     /delete                         Returns DELETE data
 *     /encoding/utf8                  Returns page containing UTF-8 data.
 *     /status/:code                   Returns given HTTP Status code.
 *      /html                           Renders an HTML Page.
 *     /robots.txt                     Returns some robots.txt rules.
 *     /deny                           Denied by robots.txt file.
 *     /cache                          Returns 200 unless an If-Modified-Since or If-None-Match header is provided,
 *                                     when it returns a 304.
 *     /cache/:n                       Sets a Cache-Control header for n seconds.
 *     /links/:n                       Returns page containing n HTML links.
 *     /image                          Returns page containing an image based on sent Accept header.
 *     /image/png                      Returns page containing a PNG image.
 *     /image/jpeg                     Returns page containing a JPEG image.
 *     /image/webp                     Returns page containing a WEBP image.
 *     /image/svg                      Returns page containing a SVG image.
 *     /xml                            Returns some XML
 *     /encoding/utf8                  Returns page containing UTF-8 data.
 *     /gzip                           Returns gzip-encoded data.
 *     /deflate                        Returns deflate-encoded data.
 *     /throw                          Returns HTTP 500 server error
 *     /someInvalidEndpoint            Returns a customized HTTP 404 json error
 *     /cookies                        Returns the cookies
 *     /cookies/set?name=value         Set new cookies
 *     /cookies/delete?name            Delete specified cookies
 *     /redirect/:n                    Redirect n times
 *     /redirect-to?url=               Redirect to an URL
 */


class HttpbinResponse(
    var args: ValuesMap? = null,
    var headers: ValuesMap? = null,
    var origin: String? = null,
    var url: String? = null,
    var `user-agent`: String? = null,
    var data: String? = null,
    var files: Map<String, PartData.FileItem>? = null,
    var form: ValuesMap? = null,
    val json: Map<String, Any>? = null,
    var gzipped: Boolean? = null,
    var deflated: Boolean? = null,
    var method: String? = null,
    var cookies: Map<String, String>? = null
)

data class HttpbinError(
    val request: ApplicationRequest,
    val message: String,
    val code: HttpStatusCode,
    val cause: Throwable? = null
)


fun Application.main() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(ConditionalHeaders)
    install(PartialContentSupport)
    install(HeadRequestSupport)
    install(CORS) {
        anyHost()
        allowCredentials = true
        listOf(HttpMethod("PATCH"), HttpMethod.Put, HttpMethod.Delete).forEach {
            method(it)
        }
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            val error = HttpbinError(code = HttpStatusCode.InternalServerError, request = call.request, message = cause.toString(), cause = cause)
            call.respond(error)
        }
    }
    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        call.transform.register { value: HttpbinResponse ->
            TextContent(Moshi.JsonResponse.toJson(value), ContentType.Application.Json)
        }
        call.transform.register { value: HttpbinError ->
            call.response.status(value.code)
            TextContent(Moshi.Errors.toJson(value), ContentType.Application.Json)
        }
    }

    val baseDir = File("ktor-samples/ktor-samples-httpbin/resources/static")
    require(baseDir.exists()) { "Cannot find ${baseDir.absolutePath}" }

    routing {
        val staticFilesMap = mapOf(
            "/" to "httpbin.1.html", // TODO: convert to freemarker
            "/xml" to "sample.xml",
            "/encoding/utf8" to "UTF-8-demo.html",
            "/html" to "moby.html",
            "/robots.txt" to "robots.txt",
            "/forms/post" to "forms-post.html",
            "/postman" to "httpbin.postman_collection.json",
            "/httpbin.js" to "httpbin.js"

        )
        for ((path, filename) in staticFilesMap) {
            get(path) {
                call.response.cacheControl(CacheControl.NoStore(null))
                call.respond(call.resolveClasspathWithPath("", "static/$filename")!!)
            }
        }
        route("/image") {
            val imageConfigs = listOf(
                ImageConfig("jpeg", ContentType.Image.JPEG, "jackal.jpg"),
                ImageConfig("png", ContentType.Image.PNG, "pig_icon.png"),
                ImageConfig("svg", ContentType.Image.SVG, "svg_logo.svg"),
                ImageConfig("webp", ContentType("image", "webp"), "wolf_1.webp"),
                ImageConfig("any", ContentType.Image.Any, "jackal.jpg")
            )
            for (config in imageConfigs) {
                accept(config.contentType) {
                    get {
                        call.respond(call.resolveClasspathWithPath("", "static/${config.filename}")!!)
                    }
                }
                route(config.path) {
                    get {
                        call.respond(call.resolveClasspathWithPath("", "static/${config.filename}")!!)
                    }
                }
            }
        }

        val postPutDelete = mapOf(
            "/post" to HttpMethod.Post,
            "/put" to HttpMethod.Put,
            "/delete" to HttpMethod.Delete,
            "/patch" to HttpMethod("PATCH")
        )
        for ((route, method) in postPutDelete) {
            route(route) {
                handleRequestWithBodyFor(method)
            }
        }

        get("/get") {
            call.sendHttpbinResponse()
        }

        get("/headers") {
            call.sendHttpbinResponse {
                clear()
                headers = call.request.headers
            }
        }

        get("/ip") {
            call.sendHttpbinResponse {
                clear()
                origin = call.request.origin.remoteHost
            }
        }

        /* install(Compression) */
        get("/gzip") {
            call.sendHttpbinResponse {
                gzipped = true
            }
        }
        get("/deflate") {
            // Send header "Accept-Encoding: deflate"
            call.sendHttpbinResponse {
                deflated = true
            }
        }

        get("/cache") {
            val etag = "db7a0a2684bb439e858ee25ae5b9a5c6"
            val date: ZonedDateTime = ZonedDateTime.of(2016, 2, 15, 0, 0, 0, 0, ZoneId.of("Z")) // Kotlin 1.0
            call.withLastModified(date) {
                call.withETag(etag, putHeader = true) {
                    call.response.lastModified(date)
                    call.sendHttpbinResponse()
                }
            }

        }

        get("/cache/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            val cache = CacheControl.MaxAge(maxAgeSeconds = n, visibility = CacheControlVisibility.PUBLIC)
            call.response.cacheControl(cache)
            call.sendHttpbinResponse()
        }

        get("/user-agent") {
            call.sendHttpbinResponse {
                clear()
                `user-agent` = call.request.header("User-Agent")
            }
        }

        get("/status/{status}") {
            val status = call.parameters.get("status")?.toInt() ?: 0
            call.response.status(HttpStatusCode.fromValue(status) ?: HttpStatusCode.BadRequest)
            call.respond("")
        }

        get("/links/{n}/{m?}") {
            try {
                val nbLinks = call.parameters.get("n")!!.toInt()
                val selectedLink = call.parameters.get("m")?.toInt() ?: 0
                call.respondHtml {
                    generateLinks(nbLinks, selectedLink)
                }
            } catch (e: Throwable) {
                call.respondHtml(status = HttpStatusCode.BadRequest) {
                    invalidRequest("$e")
                }
            }
        }

        get("/deny") {
            call.respondText(ANGRY_ASCII)
        }

        get("/throw") {
            throw RuntimeException("Endpoint /throw throwed a throwable")
        }

        get("/response-headers") {
            val params = call.request.queryParameters
            val requestedHeaders = params.flattenEntries().toMap()
            for ((key, value) in requestedHeaders) {
                call.response.header(key, value)
            }
            val content = TextContent(Moshi.ValuesMap.toJson(params), ContentType.Application.Json)
            call.respond(content)
        }

        get("/redirect/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            if (n == 0) {
                call.sendHttpbinResponse()
            } else {
                call.respondRedirect("/redirect/${n-1}")
            }
        }

        get("/redirect-to") {
            val url = call.parameters.get("url")!!
            call.respondRedirect(url)
        }

        get("/relative-redirect") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("302 Relative redirects n times.")
        }

        get("/absolute-redirect/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("302 Absolute redirects n times.")
        }

        get("/cookies") {
            val rawCookies = call.request.cookies.parsedRawCookies
            call.sendHttpbinResponse {
                clear()
                cookies = rawCookies
            }
        }

        get("/cookies/set") {
            val params = call.request.queryParameters.flattenEntries()
            for ((key, value) in params) {
                call.response.cookies.append(name = key, value = value, path = "/")
            }
            val rawCookies = call.request.cookies.parsedRawCookies
            call.sendHttpbinResponse {
                clear()
                cookies = rawCookies + params.toMap()
            }
        }

        get("/cookies/delete") {
            val params = call.request.queryParameters.names()
            val rawCookies = call.request.cookies.parsedRawCookies
            for (name in params) {
                call.response.cookies.appendExpired(name, path = "/")
            }
            call.sendHttpbinResponse {
                clear()
                cookies = rawCookies.filterKeys { key -> key !in params }
            }
        }

        get("/basic-auth/{user}/{password}") {
            val user = call.parameters.get("user")!!
            val password = call.parameters.get("password")!!
            TODO("Challenges HTTPBasic Auth.")
        }

        get("/hidden-basic-auth/{user}/{password}") {
            val user = call.parameters.get("user")!!
            val password = call.parameters.get("password")!!
            TODO("404'd HTTPBasic Auth.")
        }

        get("/digest-auth/{user}/{password}") {
            val user = call.parameters.get("user")!!
            val password = call.parameters.get("password")!!
            TODO("Challenges HTTP Digest Auth.")
        }

        get("/stream/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("Streams min(n, 100) lines.")
        }

        get("/delay/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("Delays responding for min(n, 10) seconds.")
        }

        /** /drip?numbytes=n&duration=s&delay=s&code=code **/
        get("/drip") {
            TODO("Drips data over a duration after an optional initial delay, then (optionally) returns with the given status code.")
        }

        /** /range/1024?duration=s&chunk_size=code  */
        get("/range/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("Streams n bytes, and allows specifying a Range header to select a subset of the data. Accepts a chunk_size and request duration parameter.")
        }

        get("/bytes/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("Generates n random bytes of binary data, accepts optional seed integer parameter.")
        }

        get("/stream-bytes/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            TODO("Streams n random bytes of binary data, accepts optional seed and chunk_size integer parameters.")
        }

        route("{...}") {
            handle {
                val error = HttpbinError(code = HttpStatusCode.NotFound, request = call.request, message = "NOT FOUND")
                call.response.status(HttpStatusCode.NotFound)
                call.respond(error)
            }
        }

    }
}


fun Route.handleRequestWithBodyFor(method: HttpMethod): Unit {

    requestContentType(ContentType.MultiPart.FormData) {
        method(method) {
            handle {
                val content = call.request.content
                val listFiles = content.get<MultiPartData>().parts.filterIsInstance<PartData.FileItem>().toList()
                call.sendHttpbinResponse {
                    form = content.get<ValuesMap>()
                    files = listFiles.associateBy { part -> part.partName ?: "a" }
                }
            }
        }
    }
    requestContentType(ContentType.Application.FormUrlEncoded) {
        method(method) {
            handle {
                call.sendHttpbinResponse {
                    form = call.request.content.get<ValuesMap>()
                }
            }
        }
    }
    requestContentType(ContentType.Application.Json) {
        method(method) {
            handle {
                val content = call.request.content.get<String>()
                val response = HttpbinResponse(
                    data = content,
                    json = Moshi.parseJsonAsMap(content),
                    args = call.request.queryParameters,
                    headers = call.request.headers
                )
                call.respond(response)
            }
        }
    }
    method(method) {
        handle {
            call.sendHttpbinResponse {
                data = call.request.content.get<String>()
            }
        }
    }
}

