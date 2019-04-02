package io.ktor.client.engine

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Base jvm implementation for [HttpClientEngine]
 */
@Suppress("KDocMissingDocumentation")
abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    private val clientContext = CompletableDeferred<Unit>()
    private val callSupervisor = SupervisorJob(clientContext)
    private val _dispatcher by lazy { Executors.newFixedThreadPool(config.threadsCount).asCoroutineDispatcher() }

    @UseExperimental(InternalCoroutinesApi::class)
    override val dispatcher: CoroutineDispatcher get() = _dispatcher

    @UseExperimental(InternalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        _dispatcher + clientContext + CoroutineName("$engineName-context")
    }

    /**
     * Create [CoroutineContext] to execute call.
     */
    protected fun createCallContext(): CoroutineContext = coroutineContext + CompletableDeferred<Unit>(callSupervisor)

    override fun close() {
        callSupervisor.cancel()

        callSupervisor.invokeOnCompletion {
            clientContext.complete(Unit)
            _dispatcher.close()
        }
    }
}
