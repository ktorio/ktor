package io.ktor.client.engine

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Base jvm implementation for [HttpClientEngine]
 */
@Suppress("KDocMissingDocumentation")
abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    private val clientContext = SupervisorJob()
    private val _dispatcher by lazy {
        Executors.newFixedThreadPool(config.threadsCount).asCoroutineDispatcher()
    }

    @UseExperimental(InternalCoroutinesApi::class)
    override val dispatcher: CoroutineDispatcher
        get() = _dispatcher

    @UseExperimental(InternalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        _dispatcher + clientContext + CoroutineName("$engineName-context")
    }

    /**
     * Create [CoroutineContext] to execute call.
     */
    protected fun createCallContext(): CoroutineContext = coroutineContext + Job(clientContext)

    override fun close() {
        clientContext.complete()

        clientContext.invokeOnCompletion {
            _dispatcher.close()
        }
    }
}
