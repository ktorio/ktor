/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.jvm.JvmInline

private const val RefKey = "\$ref"
private const val RecursiveRefKey = "\$recursiveRef"

/**
 * Defines Union [A] | [Reference]. A lot of types like Header, Schema, MediaType, etc. can be
 * either a direct value or a reference to a definition.
 */
@Serializable(with = ReferenceOr.Companion.Serializer::class)
public sealed interface ReferenceOr<out A> {
    @Serializable
    public data class Reference(@SerialName(RefKey) public val ref: String) : ReferenceOr<Nothing>

    @JvmInline public value class Value<A>(public val value: A) : ReferenceOr<A>

    public fun valueOrNull(): A? =
        when (this) {
            is Reference -> null
            is Value -> value
        }

    public companion object {
        private const val schema: String = "#/components/schemas/"
        private const val responses: String = "#/components/responses/"
        private const val parameters: String = "#/components/parameters/"
        private const val requestBodies: String = "#/components/requestBodies/"
        private const val pathItems: String = "#/components/pathItems/"

        public fun schema(name: String): Reference = Reference("$schema$name")

        public fun <A> value(value: A): ReferenceOr<A> = Value(value)

        internal class Serializer<T>(private val dataSerializer: KSerializer<T>) :
            KSerializer<ReferenceOr<T>> {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            override val descriptor: SerialDescriptor = buildSerialDescriptor("ReferenceOr", SerialKind.CONTEXTUAL)

            override fun serialize(encoder: Encoder, value: ReferenceOr<T>) {
                when (value) {
                    is Value -> encoder.encodeSerializableValue(dataSerializer, value.value)
                    is Reference -> encoder.encodeSerializableValue(Reference.serializer(), value)
                }
            }

            override fun deserialize(decoder: Decoder): ReferenceOr<T> {
                val element: GenericElement = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
                return when {
                    element.isObject() -> {
                        val entries = element.entries().toMap()
                        when {
                            RefKey in entries -> Reference(entries[RefKey]!!.deserialize(String.serializer()))
                            RecursiveRefKey in entries -> Reference(entries[RefKey]!!.deserialize(String.serializer()))
                            else -> Value(element.deserialize(dataSerializer))
                        }
                    }
                    else -> Value(element.deserialize(dataSerializer))
                }
            }
        }
    }
}
