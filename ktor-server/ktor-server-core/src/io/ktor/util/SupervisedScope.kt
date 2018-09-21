package io.ktor.util

import kotlinx.coroutines.*
import kotlin.coroutines.*

class SupervisedScope(private val name: String, private val parentScope: CoroutineScope) : CoroutineScope {
    private val job = OneWayJob(parentScope.coroutineContext)
    override val coroutineContext: CoroutineContext = parentScope.coroutineContext + job

    fun cancel() {
        job.cancel(PoisonException(name))
    }

    private class OneWayJob(parent: CoroutineContext) : SupervisorJob(parent) {
        override fun onChildFailed(cause: Throwable) {
            // ignore
        }
    }

    private abstract class SupervisorJob(parent: CoroutineContext) : AbstractCoroutine<Unit>(parent, true) {
        val disposableHandle = parent[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                super.cancel(cause)
            }
        }

        abstract fun onChildFailed(cause: Throwable)

        override fun cancel(cause: Throwable?): Boolean {
            if (cause is PoisonException) {
                if (super.cancel(cause)) {
                    disposableHandle?.dispose()
                    return true
                }

                return false
            }

            if (cause != null) {
                onChildFailed(cause)
            }

            return false
        }
    }

    private class PoisonException(name: String) : CancellationException("Isolated scope has been cancelled: $name")
}