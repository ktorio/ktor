package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.io.*


/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
expect fun defaultSerializer(): JsonSerializer

@KtorExperimentalAPI
fun defaultAllowedContentTypes(): List<ContentType> = listOf(ContentType.Application.Json)

/**
 * [HttpClient] feature that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 * The default [allowedContentTypes] is a list which contains [ContentType.Application.Json]
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [allowedContentTypes] list.
 *
 */
class JsonFeature(
    val serializer: JsonSerializer,
    @KtorExperimentalAPI val allowedContentTypes: List<ContentType>
) {
    class Config {
        /**
         * Serialized that will be used for serializing requests bodies,
         * and de-serializing response bodies when Content-Type matches to one of [allowedContentTypes].
         *
         * Default value for [serializer] is [defultSerializer].
         * And default for [allowedContentTypes] is a list which contains [ContentType.Application.Json]
         * If you want to allow other content types to be serialized into Json object, set [allowedContentTypes].
         *
         * Note: Empty [allowedContentTypes] is not allowed. (ex: allowedContentTypes = listOf())
         *       If you force to set empty list, it would throw the exception on runtime.
         */
        var serializer: JsonSerializer? = null
        var allowedContentTypes: List<ContentType> = defaultAllowedContentTypes()
        init {
            if (allowedContentTypes.isEmpty()) {
                throw EmptyContentTypeListException()
            }
        }
    }

    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature {
            val config = Config().apply(block)
            val serializer = config.serializer ?: defaultSerializer()
            val allowedContentTypes = config.allowedContentTypes

            return JsonFeature(serializer, allowedContentTypes)
        }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.allowedContentTypes.forEach { context.accept(it) }

                if (feature.allowedContentTypes.none { context.contentType()?.match(it) == true })
                    return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                if (payload is EmptyContent) {
                    proceedWith(feature.serializer.write(Unit))
                    return@intercept
                }

                proceedWith(feature.serializer.write(payload))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                if (feature.allowedContentTypes.none { context.response.contentType()?.match(it) == true })
                    return@intercept
                try {
                    proceedWith(HttpResponseContainer(info, feature.serializer.read(info, body.readRemaining())))
                } finally {
                    context.close()
                }
            }
        }
    }
}

internal class EmptyContentTypeListException : Throwable("at least one Content-Type should be given: default is listOf(ContentType.Application.Json)")

/**
 * Install [JsonFeature].
 */
fun HttpClientConfig<*>.Json(block: JsonFeature.Config.() -> Unit) {
    install(JsonFeature, block)
}
