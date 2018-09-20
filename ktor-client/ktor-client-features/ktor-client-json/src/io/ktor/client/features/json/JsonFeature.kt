package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.call.TypeInfo
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.ByteReadChannel
import kotlinx.io.charsets.Charsets


/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
expect fun defaultSerializer(): JsonSerializer

/**
 * Platform support for unwrapping generic parameters of JsonContent
 */
internal expect fun unwrapJsonContent(typeInfo: TypeInfo) : TypeInfo

/**
 * [HttpClient] feature that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is `application/json`
 */
class JsonFeature(val serializer: JsonSerializer) {
    class Config {
        /**
         * Serialized that will be used for serializing requests bodies,
         * and de-serializing response bodies when Content-Type matches `application/json`.
         *
         * Default value is [defultSerializer]
         */
        var serializer: JsonSerializer? = null
    }

    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature =
            Config().apply(block).let { JsonFeature(it.serializer ?: defaultSerializer()) }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->

                context.accept(ContentType.Application.Json)
                // Only transform if payload is JsonContent.. ie. let manual json serialized pass through
                if (context.contentType()?.match(ContentType.Application.Json) != true || payload !is JsonContent<*>) {
                    return@intercept
                }

                context.headers.remove(HttpHeaders.ContentType)
                proceedWith(feature.serializer.write(payload.value))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, response) ->
                if (context.response.contentType()?.match(ContentType.Application.Json) == true){
                    val responseToParse = when (response){
                        is HttpResponse -> response
                        is String -> ResponseWithContent(context.response, response) // Undo HttpPlainText feature
                        else -> return@intercept
                    }
                    val unwrappedInfo = unwrapJsonContent(info)
                    proceedWith(HttpResponseContainer(unwrappedInfo, JsonContent(feature.serializer.read(unwrappedInfo, responseToParse))))
                } else {
                    return@intercept
                }
            }
        }
    }
}

private class ResponseWithContent(response: HttpResponse, string: String) : HttpResponse by response {
    override val content: ByteReadChannel by lazy { ByteReadChannel(string, Charsets.UTF_8) }
}

data class JsonContent<T>(val value: T)
