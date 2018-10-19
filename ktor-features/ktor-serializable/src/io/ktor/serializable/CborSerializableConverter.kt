package io.ktor.serializable

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*

class CborSerializableConverter(private val cbor: CBOR = CBOR.plain) : SerializableConverter() {
    override suspend fun deserialize(
        context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>,
        contentType: ContentType,
        input: ByteReadChannel,
        serializer: KSerializer<Any>
    ): Any? {
        val bytes = input.toByteArray()
        return cbor.load(serializer, bytes)
    }

    override suspend fun serialize(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any,
        serializer: KSerializer<Any>
    ): Any? {
        return ByteArrayContent(
            bytes = cbor.dump(serializer, value),
            contentType = ContentType.Application.Cbor
        )
    }
}
