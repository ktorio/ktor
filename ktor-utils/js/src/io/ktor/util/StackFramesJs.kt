/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@Suppress("UNUSED")
internal actual interface CoroutineStackFrame {
    public actual val callerFrame: CoroutineStackFrame?
    public actual fun getStackTraceElement(): StackTraceElement?
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias StackTraceElement = Any
