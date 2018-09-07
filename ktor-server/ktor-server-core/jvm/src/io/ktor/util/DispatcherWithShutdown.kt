package io.ktor.util

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Specialized dispatcher useful for graceful shutdown
 */
@InternalAPI
class DispatcherWithShutdown(delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private var delegate: CoroutineDispatcher? = delegate

    @Volatile
    private var shutdownPhase = ShutdownPhase.None
    private val shutdownPool = lazy { Executors.newCachedThreadPool() }

    /**
     * Prepare for shutdown so we will not dispatch on [delegate] anymore. It is still possible to
     * dispatch coroutines.
     */
    fun prepareShutdown() {
        shutdownPhase = ShutdownPhase.Graceful
        delegate = null
    }

    /**
     * Complete shutdown. Any further attempts to dispatch anything will fail with [RejectedExecutionException]
     */
    fun completeShutdown() {
        shutdownPhase = ShutdownPhase.Completed
        if (shutdownPool.isInitialized()) shutdownPool.value.shutdown()
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return when (shutdownPhase) {
            ShutdownPhase.None -> delegate?.isDispatchNeeded(context) ?: true
            else -> true
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        when (shutdownPhase) {
            ShutdownPhase.None -> {
                try {
                    delegate?.dispatch(context, block) ?: return dispatch(context, block)
                } catch (rejected: RejectedExecutionException) {
                    if (shutdownPhase != ShutdownPhase.None) return dispatch(context, block)
                    throw rejected
                }
            }
            ShutdownPhase.Graceful -> {
                try {
                    shutdownPool.value.submit(block)
                } catch (rejected: RejectedExecutionException) {
                    shutdownPhase = ShutdownPhase.Completed
                    return dispatch(context, block)
                }
            }
            ShutdownPhase.Completed -> {
                block.run()
            }
        }
    }

    private enum class ShutdownPhase {
        None, Graceful, Completed
    }
}
