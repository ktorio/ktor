package io.ktor.client.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import kotlin.coroutines.*

abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    private val supervisor = SupervisorJob()

    @UseExperimental(InternalCoroutinesApi::class)
    override val dispatcher: ExperimentalCoroutineDispatcher by lazy {
        ExperimentalCoroutineDispatcher(config.threadsCount)
    }

    @UseExperimental(InternalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        dispatcher + supervisor + CoroutineName("$engineName-context")
    }

    protected fun createCallContext() = coroutineContext + CompletableDeferred<Unit>(coroutineContext[Job])

    override fun close() {
        supervisor.cancel()
        supervisor.invokeOnCompletion {
            @UseExperimental(InternalCoroutinesApi::class)
            dispatcher.close()
        }
    }
}
