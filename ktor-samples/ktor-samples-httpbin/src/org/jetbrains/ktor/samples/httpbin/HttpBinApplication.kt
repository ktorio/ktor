package org.jetbrains.ktor.samples.httpbin

import freemarker.cache.*
import kotlinx.coroutines.experimental.delay
import okio.Buffer
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.freemarker.*
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
import org.jetbrains.ktor.util.decodeBase64
import org.jetbrains.ktor.util.flattenEntries
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Httpbin Ktor Application implementing (large parts of)
 *
 *   httpbin(1) HTTP Request & Response Service https://httpbin.org/
 *
 * ENDPOINTS
 *
 *     /                               HTML page describing the service
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
 *     /delay/:n                       Delays responding for n seconds.
 *     /stream/:n                      Streams n lines.
 *     /cache/:n                       Sets a Cache-Control header for n seconds.
 *     /bytes/:n                       Generates n random bytes of binary data
 *     /basic-auth + Authorization     Challenges HTTPBasic Auth.
 *     /basic-auth/:user/:passwd        Challenges HTTPBasic Auth.
 *     /hidden-basic-auth/:user/:passwd 404'd BasicAuth.
 */


class HttpBinResponse(
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

data class HttpBinError(
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
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(environment.classLoader, "static")
    }
    install(CORS) {
        anyHost()
        allowCredentials = true
        listOf(HttpMethod("PATCH"), HttpMethod.Put, HttpMethod.Delete).forEach {
            method(it)
        }
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            val error = HttpBinError(code = HttpStatusCode.InternalServerError, request = call.request, message = cause.toString(), cause = cause)
            call.respond(error)
        }
    }
    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        call.transform.register { value: HttpBinResponse ->
            TextContent(Moshi.JsonResponse.toJson(value), ContentType.Application.Json)
        }
        call.transform.register { value: HttpBinError ->
            call.response.status(value.code)
            TextContent(Moshi.Errors.toJson(value), ContentType.Application.Json)
        }
    }


    val staticfilesDir = File("ktor-samples/ktor-samples-httpbin/resources/static")
    require(staticfilesDir.exists()) { "Cannot find ${staticfilesDir.absolutePath}" }

    // Authorization
    val hashedUserTable = UserHashedTableAuth(table = mapOf(
        "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))


    routing {
        get("/") {
            call.respond(FreeMarkerContent("index.ftl", Unit, ""))
        }

        get("/get") {
            call.sendHttpBinResponse()
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


        get("/headers") {
            call.sendHttpBinResponse {
                clear()
                headers = call.request.headers
            }
        }

        get("/ip") {
            call.sendHttpBinResponse {
                clear()
                origin = call.request.origin.remoteHost
            }
        }

        /* install(Compression) */
        get("/gzip") {
            call.sendHttpBinResponse {
                gzipped = true
            }
        }
        get("/deflate") {
            // Send header "Accept-Encoding: deflate"
            call.sendHttpBinResponse {
                deflated = true
            }
        }

        get("/cache") {
            val etag = "db7a0a2684bb439e858ee25ae5b9a5c6"
            val date: ZonedDateTime = ZonedDateTime.of(2016, 2, 15, 0, 0, 0, 0, ZoneId.of("Z")) // Kotlin 1.0
            call.withLastModified(date) {
                call.withETag(etag, putHeader = true) {
                    call.response.lastModified(date)
                    call.sendHttpBinResponse()
                }
            }
        }

        get("/cache/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            val cache = CacheControl.MaxAge(maxAgeSeconds = n, visibility = CacheControlVisibility.PUBLIC)
            call.response.cacheControl(cache)
            call.sendHttpBinResponse()
        }

        get("/user-agent") {
            call.sendHttpBinResponse {
                clear()
                `user-agent` = call.request.header("User-Agent")
            }
        }

        get("/status/{status}") {
            val status = call.parameters["status"]?.toInt() ?: 0
            call.respond(HttpStatusCode.fromValue(status) ?: HttpStatusCode.BadRequest)
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
                call.sendHttpBinResponse()
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
            call.sendHttpBinResponse {
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
            call.sendHttpBinResponse {
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
            call.sendHttpBinResponse {
                clear()
                cookies = rawCookies.filterKeys { key -> key !in params }
            }
        }

        route("/basic-auth") {
            authentication {
                basicAuthentication("ktor-samples-httpbin") { hashedUserTable.authenticate(it) }
            }
            get {
                call.sendHttpBinResponse()
            }
        }

        get("/basic-auth/{user}/{password}") {
            val credentials = call.parameters.run {
                UserPasswordCredential(get("user")!!, get("password")!!)
            }
            val userIdPrincipal = hashedUserTable.authenticate(credentials)
            if (userIdPrincipal == null) {
                call.response.status(HttpStatusCode.Unauthorized)
            } else {
                call.sendHttpBinResponse()
            }
        }

        get("/hidden-basic-auth/{user}/{password}") {
            call.response.status(HttpStatusCode.Unauthorized)
        }

        get("/stream/{n}") {
            val buffer = Buffer()
            val lorenIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"
            val times = call.parameters["n"]!!.toInt()
            repeat(times) {
                buffer.writeUtf8(lorenIpsum)
            }
            call.respondText(buffer.readUtf8())
        }

        get("/delay/{n}") {
            val n = call.parameters["n"]!!.toLong()
            require(n in 0..10) { "Expected a number of seconds between 0 and 10" }
            delay(n, TimeUnit.SECONDS)
            call.sendHttpBinResponse()
        }

        get("/bytes/{n}") {
            val n = call.parameters.get("n")!!.toInt()
            val r = Random()
            val buffer = ByteArray(n) { r.nextInt().toByte() }
            call.respond(buffer)
        }

        val staticFilesMap = mapOf(
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
                call.respond(call.resolveClasspathWithPath("", "static/$filename")!!)
            }
        }

        // http://localhost:8080/static/httpbin.1.html for example will show the documentation
        route("/static/") {
            serveFileSystem(staticfilesDir)
        }

        route("{...}") {
            handle {
                val error = HttpBinError(code = HttpStatusCode.NotFound, request = call.request, message = "NOT FOUND")
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
                val listFiles = call.request.receive<MultiPartData>().parts.filterIsInstance<PartData.FileItem>().toList()
                call.sendHttpBinResponse {
                    form = call.request.receive<ValuesMap>()
                    files = listFiles.associateBy { part -> part.partName ?: "a" }
                }
            }
        }
    }
    requestContentType(ContentType.Application.FormUrlEncoded) {
        method(method) {
            handle {
                call.sendHttpBinResponse {
                    form = call.request.receive<ValuesMap>()
                }
            }
        }
    }
    requestContentType(ContentType.Application.Json) {
        method(method) {
            handle {
                val content = call.request.receive<String>()
                val response = HttpBinResponse(
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
            call.sendHttpBinResponse {
                data = call.request.receive<String>()
            }
        }
    }
}
