package io.ktor.client.engine.curl

import kotlin.native.concurrent.*

class ListenerKey

interface WorkerRequest {
    val listenerKey: ListenerKey
}

interface WorkerResponse {
    val listenerKey: ListenerKey
}

interface WorkerListener<R : WorkerResponse> {
    fun update(data: R)
}

open class WorkerProcessor<Q : WorkerRequest, R : WorkerResponse> {
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
                listeners[it.listenerKey]?.update(it)
            }
        }
    }
}
