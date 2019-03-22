package io.ktor.gson

import com.google.gson.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.ContentTransformationException
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import kotlin.reflect.*
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.*

/**
 * GSON converter for [ContentNegotiation] feature
 */
class GsonConverter(private val gson: Gson = Gson(), private val attemptDeserializeEmptyBodies : Boolean = false) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        return TextContent(gson.toJson(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val reader = channel.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        val type = request.type

        if (gson.isExcluded(type)) {
            throw ExcludedTypeGsonException(type)
        }

        var result = gson.fromJson(reader, type.javaObjectType)
        result = if (result == null && attemptDeserializeEmptyBodies) attemptToDeserializeEmptyBody(type) else result

        if (result == null) throw UnsupportedNullValuesException()

        return result
    }
}

/**
 * Register GSON to [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    block: GsonBuilder.() -> Unit = {},
    attemptDeserializeEmptyBodies : Boolean = false
) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create(), attemptDeserializeEmptyBodies = attemptDeserializeEmptyBodies)
    register(contentType, converter)
}

internal class ExcludedTypeGsonException(val type: KClass<*>) :
    Exception("Type ${type.jvmName} is excluded so couldn't be used in receive")

internal class UnsupportedNullValuesException() :
    ContentTransformationException("Receiving null values is not supported")

private fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)
