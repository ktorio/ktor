package io.ktor.client.engine.curl

import kotlin.native.concurrent.*

internal class ListenerKey

internal interface WorkerRequest {
    val key: ListenerKey
}

internal interface WorkerResponse {
    val key: ListenerKey
}

internal interface WorkerListener<R : WorkerResponse> {
    fun update(data: R)
}

internal open class WorkerProcessor<Q : WorkerRequest, R : WorkerResponse> {
    private val listeners = mutableMapOf<ListenerKey, WorkerListener<R>>()
    protected val worker = Worker.start()
    protected val pendingFutures = mutableSetOf<Future<R>>()

    fun addListener(key: ListenerKey, listener: WorkerListener<R>): ListenerKey {
        listeners[key] = listener
        return key
    }

    fun check(timeout: Int = 10) {
        val ready = pendingFutures.waitForMultipleFutures(timeout)
        for (future in ready) {
            future.consume { it ->
                listeners[it.key]?.update(it)
            }
        }
    }
}
