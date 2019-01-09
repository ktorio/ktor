package io.ktor.util

@Suppress("UNUSED")
internal actual interface CoroutineStackFrame {
    public actual val callerFrame: CoroutineStackFrame?
    public actual fun getStackTraceElement(): StackTraceElement?
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias StackTraceElement = Any
