package io.ktor.serializable

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.io.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import kotlin.collections.set
import kotlin.reflect.*

abstract class SerializableConverter : ContentConverter {
    @Suppress("UNCHECKED_CAST")
    private val serializers = mutableMapOf(
        Unit::class as KClass<Any> to UnitSerializer as KSerializer<Any>,
        Boolean::class as KClass<Any> to BooleanSerializer as KSerializer<Any>,
        Byte::class as KClass<Any> to ByteSerializer as KSerializer<Any>,
        Short::class as KClass<Any> to ShortSerializer as KSerializer<Any>,
        Int::class as KClass<Any> to IntSerializer as KSerializer<Any>,
        Long::class as KClass<Any> to LongSerializer as KSerializer<Any>,
        Float::class as KClass<Any> to FloatSerializer as KSerializer<Any>,
        Double::class as KClass<Any> to DoubleSerializer as KSerializer<Any>,
        Char::class as KClass<Any> to CharSerializer as KSerializer<Any>,
        String::class as KClass<Any> to StringSerializer as KSerializer<Any>
    )

    /** Register objects of [type] to be serialized using [serializer]. */
    fun <T : Any> register(type: KClass<T>, serializer: KSerializer<T>) {
        @Suppress("UNCHECKED_CAST")
        serializers[type as KClass<Any>] = serializer as KSerializer<Any>
    }

    /** Register objects of type [T] to be serialized using [serializer]. */
    inline fun <reified T : Any> register(serializer: KSerializer<T>) {
        register(T::class, serializer)
    }

    /** Register objects of type [T] to be serialized. */
    inline fun <reified T : Any> register() {
        register(T::class.serializer())
    }

    private fun getSerializer(type: KClass<*>): KSerializer<Any>? {
        return serializers[type]
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val subject = context.subject
        val input = subject.value as? ByteReadChannel ?: return null
        val serializer = getSerializer(subject.type) ?: return null
        val contentType = context.call.request.contentType()

        return deserialize(context, contentType, input, serializer)
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        val serializer = getSerializer(value::class) ?: return null

        return serialize(context, contentType, value, serializer)
    }

    protected abstract suspend fun deserialize(
        context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>,
        contentType: ContentType,
        input: ByteReadChannel,
        serializer: KSerializer<Any>
    ): Any?

    protected abstract suspend fun serialize(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any,
        serializer: KSerializer<Any>
    ): Any?
}

/**
 * Register multiple [SerializableConverter]s for JSON, CBOR or ProtoBuf with the [ContentNegotiation] feature.
 *
 * @param json The [JSON] instance to use, or `null` to disable JSON support.
 * @param cbor The [CBOR] instance to use, or `null` to disable CBOR support.
 * @param protoBuf The [ProtoBuf] instance to use, or `null` to disable ProtoBuf support. Disabled by default since
 * [SerialId] is required on [Serializable] classes.
 * @param block Used to register [KSerializer]s using [SerializableConverter.register]. Called on each
 * `SerializableConverter`.
 */
inline fun ContentNegotiation.Configuration.serializable(
    json: JSON? = JSON.plain,
    cbor: CBOR? = CBOR.plain,
    protoBuf: ProtoBuf? = null,
    block: SerializableConverter.() -> Unit
) {
    if (json != null) {
        register(ContentType.Application.Json, JsonSerializableConverter(json).apply(block))
    }

    if (cbor != null) {
        register(ContentType.Application.Cbor, CborSerializableConverter(cbor).apply(block))
    }

    if (protoBuf != null) {
        register(ContentType.Application.ProtoBuf, ProtoBufSerializableConverter(protoBuf).apply(block))
    }
}
