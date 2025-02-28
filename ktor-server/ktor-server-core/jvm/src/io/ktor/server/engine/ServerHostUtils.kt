/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/**
 * Obtain function FQName.
 */
internal fun Function<*>.methodName(): String {
    val method = (this as? KFunction<*>)?.javaMethod ?: return "${javaClass.name}.invoke"

    val className = method.declaringClass.name
    val name = method.name
    return "$className.$name"
}
