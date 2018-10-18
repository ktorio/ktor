package io.ktor.server.engine

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * A helper class useful with an [ApplicationEngine] implementation to handle parent context cancellation
 */
@EngineAPI
@KtorExperimentalAPI
class EngineContextCancellationHelper(private val parent: CoroutineContext,
                                      private val actualStop: () -> Unit) : CoroutineScope {
    constructor(engine: ApplicationEngine) : this(engine.environment.parentCoroutineContext, {
        engine.stop(1, 5, TimeUnit.SECONDS)
    })

    private val engineJob = atomic<Job?>(null)

    override val coroutineContext: CoroutineContext get() = engineJob.value ?: throw IllegalStateException("Server is not yet started")

    /**
     * Should be called from engine.start
     */
    fun start() {
        engineJob.updateAndGet { before ->
            if (before != null) throw IllegalStateException("Engine is already running")
            newJob()
        }?.start()
    }

    /**
     * Should be called from engine.stop
     */
    fun stop() {
        engineJob.getAndSet(null)?.cancel()
    }

    private fun newJob(): Job = GlobalScope.launch(parent, start = CoroutineStart.LAZY) {
        try {
            suspendCancellableCoroutine<Unit> {
                // suspend forever until cancellation
            }
        } catch (t: Throwable) {
            val me: Job = coroutineContext[Job]!!
            if (engineJob.compareAndSet(me, null)) {
                actualStop()
            }
        }
    }
}
