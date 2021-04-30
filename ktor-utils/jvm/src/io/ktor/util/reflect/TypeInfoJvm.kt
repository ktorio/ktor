/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import java.lang.reflect.*
import java.lang.reflect.Type
import kotlin.reflect.*

public actual typealias Type = java.lang.reflect.Type

@PublishedApi
internal open class TypeBase<T>

public actual inline fun <reified T> typeInfo(): TypeInfo {
    val base = object : TypeBase<T>() {}
    val superType = base::class.java.genericSuperclass!!

    val reifiedType = (superType as ParameterizedType).actualTypeArguments.first()!!
    return typeInfoImpl(reifiedType, T::class, tryGetType<T>())
}

public fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType?): TypeInfo =
    TypeInfo(kClass, reifiedType, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.java.isInstance(this)

@OptIn(ExperimentalStdlibApi::class)
public actual val KType.platformType: Type
    get() = javaType
