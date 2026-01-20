/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlin.jvm.JvmInline

private const val RefKey = $$"$ref"
private const val DynamicRefKey = $$"$dynamicRef"

/**
 * Defines Union [A] | [Reference]. A lot of types like Header, Schema, MediaType, etc. can be
 * either a direct value or a reference to a definition.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ReferenceOr)
 */
@Serializable(with = ReferenceOr.Companion.Serializer::class)
public sealed interface ReferenceOr<out A> {
    /**
     * A reference to a definition within the current document.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ReferenceOr.Reference)
     *
     * @property ref Reference to a definition like #/components/schemas/Name
     * @property isDynamic Whether this reference is dynamic.
     */
    public data class Reference(
        public val ref: String,
        public val isDynamic: Boolean = false,
    ) : ReferenceOr<Nothing>

    @JvmInline public value class Value<A>(public val value: A) : ReferenceOr<A>

    /**
     * Returns the value if this instance is of type [Value], or null if it is of type [Reference].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ReferenceOr.valueOrNull)
     *
     * @return The value of type [A] if this is a [Value], or null if this is a [Reference].
     */
    public fun valueOrNull(): A? =
        when (this) {
            is Reference -> null
            is Value -> value
        }

    /**
     * Maps the value using the given function.
     *
     * In the case of Reference, this is a no-op.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ReferenceOr.mapValue)
     */
    public fun <B> mapValue(mappingFunction: (A) -> B): ReferenceOr<B> =
        when (this) {
            is Reference -> this
            is Value -> Value(mappingFunction(value))
        }

    /**
     * Same as map, but for mapping to references.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ReferenceOr.mapToReference)
     */
    public fun <B> mapToReference(mappingFunction: (A) -> ReferenceOr<B>): ReferenceOr<B> =
        when (this) {
            is Reference -> this
            is Value -> mappingFunction(value)
        }

    public companion object {
        private const val schema: String = "#/components/schemas/"
        private const val responses: String = "#/components/responses/"
        private const val parameters: String = "#/components/parameters/"
        private const val requestBodies: String = "#/components/requestBodies/"
        private const val pathItems: String = "#/components/pathItems/"

        public fun schema(name: String, dynamic: Boolean = false): Reference = Reference("$schema$name", dynamic)

        public fun <A> value(value: A): ReferenceOr<A> = Value(value)

        internal class Serializer<T>(
            private val dataSerializer: KSerializer<T>
        ) : KSerializer<ReferenceOr<T>> {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            override val descriptor: SerialDescriptor =
                buildSerialDescriptor("io.ktor.openapi.ReferenceOr", SerialKind.CONTEXTUAL)
            private val refDescriptor = buildClassSerialDescriptor("io.ktor.openapi.ReferenceOr.ref") {
                element<String>(RefKey)
            }
            private val dynamicRefDescriptor =
                buildClassSerialDescriptor("io.ktor.openapi.ReferenceOr.dynamicRef") {
                    element<String>(DynamicRefKey)
                }

            override fun serialize(encoder: Encoder, value: ReferenceOr<T>) {
                when (value) {
                    is Value -> encoder.encodeSerializableValue(dataSerializer, value.value)
                    is Reference -> encodeReference(value, encoder)
                }
            }

            private fun encodeReference(value: Reference, encoder: Encoder) {
                if (value.isDynamic) {
                    encoder.encodeStructure(dynamicRefDescriptor) {
                        encodeStringElement(dynamicRefDescriptor, 0, value.ref)
                    }
                } else {
                    encoder.encodeStructure(refDescriptor) {
                        encodeStringElement(refDescriptor, 0, value.ref)
                    }
                }
            }

            override fun deserialize(decoder: Decoder): ReferenceOr<T> {
                val element: GenericElement = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
                return when {
                    element.isObject() -> {
                        val entries = element.entries().toMap()
                        when {
                            RefKey in entries ->
                                Reference(entries[RefKey]!!.deserialize(String.serializer()))
                            DynamicRefKey in entries ->
                                Reference(
                                    entries[DynamicRefKey]!!.deserialize(String.serializer()),
                                    isDynamic = true
                                )
                            else -> Value(element.deserialize(dataSerializer))
                        }
                    }
                    else -> Value(element.deserialize(dataSerializer))
                }
            }
        }
    }
}
