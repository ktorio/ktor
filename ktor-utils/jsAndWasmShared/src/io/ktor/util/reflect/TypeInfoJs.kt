/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

@Deprecated("Not used anymore in common code as it was effective only on JVM target.")
public actual interface Type

@Suppress("DEPRECATION")
@Deprecated("Not used anymore in common code as it was effective only on JVM target.")
public object JsType : Type

@Suppress("DEPRECATION")
@Deprecated("Use TypeInfo constructor instead.", ReplaceWith("TypeInfo(kClass, kType)"))
public fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType?): TypeInfo = TypeInfo(kClass, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)

@Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
@Deprecated("Not used anymore in common code as it was effective only on JVM target.")
public actual val KType.platformType: Type
    get() = JsType
