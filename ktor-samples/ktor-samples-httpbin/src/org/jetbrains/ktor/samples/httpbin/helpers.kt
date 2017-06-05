package org.jetbrains.ktor.samples.httpbin

import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.jetbrains.ktor.application.ApplicationCall
import org.jetbrains.ktor.application.ApplicationRequest
import org.jetbrains.ktor.cio.WriteChannel
import org.jetbrains.ktor.cio.toOutputStream
import org.jetbrains.ktor.content.CacheControl
import org.jetbrains.ktor.content.FinalContent
import org.jetbrains.ktor.content.Resource
import org.jetbrains.ktor.content.Version
import org.jetbrains.ktor.features.origin
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.withCharset
import org.jetbrains.ktor.request.MultiPartData
import org.jetbrains.ktor.request.PartData
import org.jetbrains.ktor.request.httpMethod
import org.jetbrains.ktor.util.ValuesMap
import java.util.*


/** By default send what is expected for /get
 ** Use a lambda to customize the response  **/
suspend fun ApplicationCall.sendHttpbinResponse(configure: HttpbinResponse.() -> Unit = {}) {
    val response = HttpbinResponse(
        args = request.queryParameters,
        headers = request.headers,
        url = request.url(),
        origin = request.origin.remoteHost,
        method = request.origin.method.value
    )
    response.configure()
    respond(response)
}

fun HttpbinResponse.clear() {
    args = null
    headers = null
    url = null
    origin = null
    method = null
}


fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    val url = "${origin.scheme}://${origin.host}$port${origin.uri}"
    return url
}


/** /links/:n/:m **/
fun HTML.generateLinks(nbLinks: Int, selectedLink: Int) {
    head {
        title {
            +"Links"
        }
    }
    body {
        for (i in 0.rangeTo(nbLinks - 1)) {
            a {
                if (i != selectedLink) {
                    attributes["href"] = "/links/$nbLinks/$i"
                }
                +"$i"
            }
        }
    }
}

fun HTML.invalidRequest(message: String) {
    head {
        title {
            +"Invalid Request"
        }
    }
    body {
        h1 {
            +"Invalid request"
        }
        p {
            code { +message }
        }
    }
}


/** Reused from ktor-samples-html **/

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    respond(HtmlContent(status, builder = block))
}

suspend fun ApplicationCall.respondHtml(status: HttpStatusCode = HttpStatusCode.OK,
                                        versions: List<Version> = emptyList(),
                                        cacheControl: CacheControl? = null,
                                        block: HTML.() -> Unit) {
    respond(HtmlContent(status, versions, cacheControl, builder = block))
}

class HtmlContent(override val status: HttpStatusCode? = null,
                  override val versions: List<Version> = emptyList(),
                  override val cacheControl: CacheControl? = null,
                  val builder: HTML.() -> Unit) : Resource, FinalContent.WriteChannelContent() {

    override val contentType: ContentType
        get() = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    override val expires = null
    override val contentLength = null
    override val headers by lazy { super<Resource>.headers }

    override suspend fun writeTo(channel: WriteChannel) {
        val writer = channel.toOutputStream().bufferedWriter()
        writer.use {
            it.append("<!DOCTYPE html>\n")
            it.appendHTML().html(builder)
        }
        channel.close()
    }
}


/** moshi Json Library ***/

object Moshi {

    val moshi = Moshi.Builder()
        .add(MapAdapter())
        .build()


    val Map = moshi.adapter(Map::class.java).lenient()

    val JsonResponse = moshi.adapter(HttpbinResponse::class.java)

    /** See MapAdapter#serializeHttpbinError **/
    val Errors = moshi.adapter(HttpbinError::class.java)

    val ValuesMap = moshi.adapter(ValuesMap::class.java)

    fun parseJsonAsMap(json: String): Map<String, Any>? = try {
        @Suppress("UNCHECKED_CAST")
        Map.fromJson(json) as Map<String, Any>
    } catch (e: Exception) {
        println(e.message)
        null
    }


    private class MapAdapter {

        @ToJson fun serializeHttpbinError(error: HttpbinError) : SerializedError {
            val stacktrace =
                if (error.cause == null) null
                else error.cause.stackTrace.take(3).map { e -> e.toString() }
            val result = SerializedError(
                message = error.message,
                method = error.request.httpMethod.value,
                url = error.request.url(),
                stacktrace = stacktrace
            )
            return result
        }

        @ToJson fun serializeFilePart(part: PartData.FileItem) : String {
            return "File ${part.originalFileName} of type ${part.contentType}"
        }


        @ToJson fun serializeValuesMap(parseMap: ValuesMap): Map<Any, Any> {
            val result = LinkedHashMap<Any, Any>()
            for ((key, value) in parseMap.entries()) {
                if (value.size == 1) {
                    result.put(key, value[0])
                } else {
                    result.put(key, value)
                }
            }
            return result
        }

        @ToJson fun MultiPartData(multiPartData: MultiPartData?): Map<Any, Any>? {
            if (multiPartData == null) {
                return null
            }
            val result = LinkedHashMap<Any, Any>()
            for (part in multiPartData.parts) {
                when (part) {
                    is PartData.FormItem -> result.put(part.partName!!, part.value)
                    is PartData.FileItem -> result.put(part.partName!!, "A file of type ${part.contentType}")
                }
            }
            return result
        }


        @FromJson fun unserializeMultiPartDataserialize(map: Map<String, String>): MultiPartData?
            = TODO("Required by moshi but not needed")
        @FromJson fun unserializeValuesMap(map: Map<String, String>): ValuesMap
            = TODO("Required by moshi but not needed")
        @FromJson fun unserializeHttpbinError(map: SerializedError) : HttpbinError
            = TODO("Required by moshi but not needed")
        @FromJson fun unserializeFilePart(json: String) : PartData.FileItem
            = TODO("Required by moshi but not needed")
    }



}

private data class SerializedError(val message: String, val method: String, val url: String, val stacktrace: List<String>?)

data class ImageConfig(val path: String, val contentType: ContentType, val filename: String)

/** For /deny **/
val ANGRY_ASCII = """
          .-''''''-.
        .' _      _ '.
       /   O      O   \\
      :                :
      |                |
      :       __       :
       \  .-"`  `"-.  /
        '.          .'
          '-......-'
     YOU SHOULDN'T BE HERE
"""



