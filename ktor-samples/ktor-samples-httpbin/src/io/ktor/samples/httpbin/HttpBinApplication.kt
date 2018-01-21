package io.ktor.samples.httpbin

import com.google.gson.*
import com.google.gson.reflect.*
import kotlinx.coroutines.experimental.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.*
import java.time.*
import java.util.*
import java.util.concurrent.*

/**
 * HttpBin Ktor Application implementing (large parts of)
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

val gson: Gson = GsonBuilder().setPrettyPrinting().create()

fun Application.main() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(ConditionalHeaders)
    install(PartialContent)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, GsonConverter())
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
            environment.log.error(cause)
            val error = HttpBinError(code = HttpStatusCode.InternalServerError, request = call.request.local.uri, message = cause.toString(), cause = cause)
            call.respond(error)
        }
    }

    val staticfilesDir = File("ktor-samples/ktor-samples-httpbin/resources/static")
    require(staticfilesDir.exists()) { "Cannot find ${staticfilesDir.absolutePath}" }

    // Authorization
    val hashedUserTable = UserHashedTableAuth(table = mapOf(
            "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))


    routing {
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
            for ((path, contentType, filename) in imageConfigs) {
                accept(contentType) {
                    resource("", "static/$filename")
                }
                resource(path, "static/$filename")
            }
        }


        get("/headers") {
            call.sendHttpBinResponse {
                clear()
                headers = call.request.headers.toMap()
            }
        }

        get("/ip") {
            call.sendHttpBinResponse {
                clear()
                origin = call.request.origin.remoteHost
            }
        }

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
            val n = call.parameters["n"]!!.toInt()
            val cache = CacheControl.MaxAge(maxAgeSeconds = n, visibility = CacheControl.Visibility.Public)
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
            call.respond(HttpStatusCode.fromValue(status))
        }

        get("/links/{n}/{m?}") {
            try {
                val nbLinks = call.parameters["n"]!!.toInt()
                val selectedLink = call.parameters["m"]?.toInt() ?: 0
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
            val content = TextContent(gson.toJson(params), ContentType.Application.Json)
            call.respond(content)
        }

        get("/redirect/{n}") {
            val n = call.parameters["n"]!!.toInt()
            if (n == 0) {
                call.sendHttpBinResponse()
            } else {
                call.respondRedirect("/redirect/${n - 1}")
            }
        }

        get("/redirect-to") {
            val url = call.parameters["url"]!!
            call.respondRedirect(url)
        }

        get("/relative-redirect") {
            val n = call.parameters["n"]!!.toInt()
            TODO("302 Relative redirects n times.")
        }

        get("/absolute-redirect/{n}") {
            val n = call.parameters["n"]!!.toInt()
            TODO("302 Absolute redirects n times.")
        }

        get("/cookies") {
            val rawCookies = call.request.cookies.rawCookies
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
            val rawCookies = call.request.cookies.rawCookies
            call.sendHttpBinResponse {
                clear()
                cookies = rawCookies + params.toMap()
            }
        }

        get("/cookies/delete") {
            val params = call.request.queryParameters.names()
            val rawCookies = call.request.cookies.rawCookies
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
            get("{user}/{password}") {
                call.sendHttpBinResponse()
            }
        }

        get("/hidden-basic-auth/{user}/{password}") {
            call.respond(HttpStatusCode.Unauthorized)
        }

        get("/stream/{n}") {
            val lorenIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"
            val times = call.parameters["n"]!!.toInt()
            call.respondWrite {
                repeat(times) {
                    write(lorenIpsum)
                    flush()
                }
            }
        }

        get("/delay/{n}") {
            val n = call.parameters["n"]!!.toLong()
            require(n in 0..10) { "Expected a number of seconds between 0 and 10" }
            delay(n, TimeUnit.SECONDS)
            call.sendHttpBinResponse()
        }

        get("/bytes/{n}") {
            val n = call.parameters["n"]!!.toInt()
            val r = Random()
            val buffer = ByteArray(n) { r.nextInt().toByte() }
            call.respond(buffer)
        }

        static {
            staticBasePackage = "static"

            defaultResource("index.html")
            resource("xml", "sample.xml")
            resource("encoding/utf8", "UTF-8-demo.html")
            resource("html", "moby.html")
            resource("robots.txt")
            resource("forms/post", "forms-post.html")
            resource("postman", "httpbin.postman_collection.json")
            resource("httpbin.js")

            route("static") {
                files(staticfilesDir)
            }
        }

        route("{...}") {
            handle {
                val error = HttpBinError(code = HttpStatusCode.NotFound, request = call.request.local.uri, message = "NOT FOUND")
                call.respond(error)
            }
        }

    }
}


fun Route.handleRequestWithBodyFor(method: HttpMethod): Unit {
    contentType(ContentType.MultiPart.FormData) {
        method(method) {
            handle {
                val listFiles = call.receive<MultiPartData>().readAllParts().filterIsInstance<PartData.FileItem>()
                call.sendHttpBinResponse {
                    form = call.receive<Parameters>()
                    files = listFiles.associateBy { part -> part.partName ?: "a" }
                }
            }
        }
    }
    contentType(ContentType.Application.FormUrlEncoded) {
        method(method) {
            handle {
                call.sendHttpBinResponse {
                    form = call.receive<Parameters>()
                }
            }
        }
    }
    contentType(ContentType.Application.Json) {
        method(method) {
            handle {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val content = call.receive<String>()
                val response = HttpBinResponse(
                        data = content,
                        json = gson.fromJson(content, type),
                        parameters = call.request.queryParameters,
                        headers = call.request.headers.toMap()
                )
                call.respond(response)
            }
        }
    }
    method(method) {
        handle {
            call.sendHttpBinResponse {
                data = call.receive<String>()
            }
        }
    }
}
