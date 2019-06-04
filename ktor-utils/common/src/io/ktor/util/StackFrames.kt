/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

internal expect class StackTraceElement

internal expect interface CoroutineStackFrame {
    public val callerFrame: CoroutineStackFrame?
    public fun getStackTraceElement(): StackTraceElement?
}
