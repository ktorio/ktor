package io.ktor.gson

import com.google.gson.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*

/**
 * GSON converter for [ContentNegotiation] feature
 */
class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {
    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent(gson.toJson(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val reader = channel.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        val type = request.type
        return gson.fromJson(reader, type.javaObjectType)
    }
}

/**
 * Register GSON to [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.gson(contentType: ContentType = ContentType.Application.Json,
                                          block: GsonBuilder.() -> Unit = {}) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create())
    register(contentType, converter)
}
