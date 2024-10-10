/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

@Deprecated("Not used anymore in common code as it was needed only for JVM target.")
public actual typealias Type = java.lang.reflect.Type

/** Type with substituted generics. */
@OptIn(ExperimentalStdlibApi::class)
public val TypeInfo.reifiedType: java.lang.reflect.Type
    // Fallback to a type without generics if we couldn't get KType.
    // For example, when class signature was stripped by ProGuard/R8
    get() = kotlinType?.javaType ?: type.java

@Suppress("DEPRECATION")
@Deprecated("Use TypeInfo constructor instead.", ReplaceWith("TypeInfo(kClass, kType)"))
public fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType?): TypeInfo = TypeInfo(kClass, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.java.isInstance(this)

@Suppress("DEPRECATION")
@Deprecated("Use KType.javaType instead.", ReplaceWith("this.javaType", "kotlin.reflect.javaType"))
@OptIn(ExperimentalStdlibApi::class)
public actual val KType.platformType: Type
    get() = javaType
