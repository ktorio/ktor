package io.ktor.utils.io.internal

import java.lang.reflect.*


internal sealed class CoroutinesEventLoop {

    /**
     * Processes next event in the current thread's event loop.
     *
     * The result of this function is to be interpreted like this:
     * * `<= 0` -- there are potentially more events for immediate processing;
     * * `> 0` -- a number of nanoseconds to wait for the next scheduled event;
     * * [Long.MAX_VALUE] -- no more events, or was invoked from the wrong thread.
     */
    internal abstract fun processEventLoop(): Long

    internal object Stub : CoroutinesEventLoop() {
        override fun processEventLoop(): Long {
            return Long.MAX_VALUE
        }
    }

    internal object FutureReflectionImpl : CoroutinesEventLoop() {
        private val clazz = try {
            Class.forName("kotlinx.coroutines.EventLoopKt")
        } catch (t: Throwable) {
            null
        }

        private val method: Method? =
            clazz?.methods?.singleOrNull {
                it.name == "processNextEventInCurrentThread"
                    && it.returnType == Long::class.javaPrimitiveType
                    && it.parameterTypes.isEmpty()
                    && Modifier.isStatic(it.modifiers)
            }

        val isApplicable = method != null

        override fun processEventLoop(): Long {
            return method!!.invoke(null) as Long
        }
    }
}

private val eventLoop: CoroutinesEventLoop =
    CoroutinesEventLoop.FutureReflectionImpl.takeIf { it.isApplicable } ?: CoroutinesEventLoop.Stub

// here we have it for future compatibility with kx coroutines
internal fun detectEventLoop(): CoroutinesEventLoop = eventLoop

