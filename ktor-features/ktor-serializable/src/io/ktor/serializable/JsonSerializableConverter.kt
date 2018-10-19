package io.ktor.serializable

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class JsonSerializableConverter(private val json: JSON = JSON.plain) : SerializableConverter() {
    override suspend fun deserialize(
        context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>,
        contentType: ContentType,
        input: ByteReadChannel,
        serializer: KSerializer<Any>
    ): Any? {
        val text = input.toInputStream().reader(contentType.charset() ?: Charsets.UTF_8).readText()
        return json.parse(serializer, text)
    }

    override suspend fun serialize(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any,
        serializer: KSerializer<Any>
    ): Any? {
        return TextContent(
            text = json.stringify(serializer, value),
            contentType = ContentType.Application.Json.withCharset(context.call.suitableCharset())
        )
    }
}
