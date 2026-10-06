/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import io.ktor.utils.io.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Information about type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.reflect.Type)
 */
@Deprecated("Not used anymore in common code as it was needed only for JVM target.")
public expect interface Type

@Suppress("DEPRECATION")
@Deprecated("Not used anymore in common code as it was needed only for JVM target.")
public expect val KType.platformType: Type

/**
 * Ktor type information.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.reflect.TypeInfo)
 *
 * @property type Source KClass<*>
 * @property isNullable `true` when `null` is a valid value for this type. This is stored separately from [kotlinType]
 * because [kotlinType] can be `null` and we want to preserve nullability in that case.
 * @property kotlinType Kotlin reified type with all generic type parameters, or `null` when type information is
 * unavailable.
 */
public class TypeInfo @InternalAPI public constructor(
    public val type: KClass<*>,
    public val isNullable: Boolean,
    public val kotlinType: KType? = null,
) {

    @OptIn(InternalAPI::class)
    public constructor(
        type: KClass<*>,
        kotlinType: KType? = null
    ) : this(
        type = type,
        isNullable = kotlinType?.isMarkedNullable == true,
        kotlinType = kotlinType,
    )

    @Suppress("UNUSED_PARAMETER", "DEPRECATION")
    @Deprecated(
        "Use constructor without the reifiedType parameter.",
        ReplaceWith("TypeInfo(type = type, kotlinType = kotlinType)")
    )
    public constructor(
        type: KClass<*>,
        reifiedType: Type,
        kotlinType: KType? = null,
    ) : this(type, kotlinType)

    init {
        require(isNullable || kotlinType?.isMarkedNullable != true) {
            "TypeInfo for nullable kotlinType $kotlinType must have isNullable set to true"
        }
    }

    override fun hashCode(): Int {
        return 31 * (kotlinType?.hashCode() ?: type.hashCode()) + isNullable.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeInfo) return false
        if (isNullable != other.isNullable) return false

        return if (kotlinType != null || other.kotlinType != null) {
            kotlinType == other.kotlinType
        } else {
            type == other.type
        }
    }

    override fun toString(): String = "TypeInfo(${kotlinType ?: type})"
}

/**
 * Returns [TypeInfo] for the specified type [T]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.reflect.typeInfo)
 */
@OptIn(InternalAPI::class)
public inline fun <reified T> typeInfo(): TypeInfo = TypeInfo(
    type = T::class,
    isNullable = null is T,
    kotlinType = typeOfOrNull<T>(),
)

@OptIn(InternalSerializationApi::class)
@InternalAPI
@Suppress("UNCHECKED_CAST")
public fun TypeInfo.serializer(): KSerializer<out Any?> {
    val serializer = kotlinType?.let { serializer(it) } ?: type.serializer()
    return if (isNullable) (serializer as KSerializer<Any>).nullable else serializer
}

/**
 * Check [this] is instance of [type].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.reflect.instanceOf)
 */
public expect fun Any.instanceOf(type: KClass<*>): Boolean

@PublishedApi
internal inline fun <reified T> typeOfOrNull(): KType? = try {
    // We need to wrap getting a type in try catch because of:
    // - KT-42913
    // - KTOR-7479 (R8 in full mode strips class signatures)
    typeOf<T>()
} catch (_: Throwable) {
    null
}
