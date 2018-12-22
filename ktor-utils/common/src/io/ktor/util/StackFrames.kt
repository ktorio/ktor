package io.ktor.util

internal expect class StackTraceElement

internal expect interface CoroutineStackFrame {
    public val callerFrame: CoroutineStackFrame?
    public fun getStackTraceElement(): StackTraceElement?
}
