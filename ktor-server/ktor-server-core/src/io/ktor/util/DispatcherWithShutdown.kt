package io.ktor.util

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

class DispatcherWithShutdown(delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private var delegate: CoroutineDispatcher? = delegate

    @Volatile
    private var shutdownPhase = ShutdownPhase.None
    private val shutdownPool = lazy { Executors.newCachedThreadPool() }

    fun prepareShutdown() {
        shutdownPhase = ShutdownPhase.Graceful
        delegate = null
    }

    fun completeShutdown() {
        shutdownPhase = ShutdownPhase.Completed
        if (shutdownPool.isInitialized()) shutdownPool.value.shutdown()
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

    enum class ShutdownPhase {
        None, Graceful, Completed
    }
}