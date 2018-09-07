package io.ktor.client.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import kotlin.coroutines.*

@UseExperimental(InternalCoroutinesApi::class)
abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    private val supervisor = SupervisorJob()

    override val dispatcher: ExperimentalCoroutineDispatcher by lazy {
        ExperimentalCoroutineDispatcher(config.threadsCount)
    }

    override val coroutineContext: CoroutineContext by lazy {
        dispatcher + supervisor + CoroutineName("$engineName-context")
    }

    protected fun createCallContext() = coroutineContext + CompletableDeferred<Unit>(coroutineContext[Job])

    override fun close() {
        supervisor.cancel()
        supervisor.invokeOnCompletion {
            dispatcher.close()
        }
    }
}
