/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*

internal actual typealias CoroutineStackFrame = kotlin.coroutines.jvm.internal.CoroutineStackFrame

internal actual typealias StackTraceElement = java.lang.StackTraceElement

internal actual fun createStackTraceElement(
    kClass: KClass<*>,
    methodName: String,
    fileName: String,
    lineNumber: Int
): StackTraceElement {
    return java.lang.StackTraceElement(
        kClass.java.name,
        methodName,
        fileName,
        lineNumber
    )
}
