package io.ktor.client.engine.curl

import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import platform.posix.*
import libcurl.*

internal class CurlRequest(val newRequests: Set<CurlRequestData>, override val listenerKey: ListenerKey) : WorkerRequest

internal class CurlResponse(val completeResponses: Set<CurlResponseData>, override val listenerKey: ListenerKey) :
    WorkerResponse

// Only set in curl thread
@ThreadLocal
private var curlState: CurlState? = null

internal fun curlUpdate(request: CurlRequest): CurlResponse {
    request.newRequests.forEach {
        curlState!!.setupEasyHandle(it)
    }

    val readyResponses = curlState!!.singleIteration(100)

    return CurlResponse(readyResponses, request.listenerKey).freeze()
}


internal class CurlProcessor : WorkerProcessor<CurlRequest, CurlResponse>() {
    fun start() {
        worker.execute(TransferMode.SAFE, { "dummy" }, {
            if (curlState == null)
                curlState = CurlState()
            else
                throw CurlEngineCreationException("An attempt to initialize curl twice.")
        })
    }

    fun requestJob(request: CurlRequest) {
        pendingFutures += worker.execute(TransferMode.SAFE, { request.freeze() }, ::curlUpdate)
    }

    fun close() {
        worker.execute(TransferMode.SAFE, { "dummy" }, { curlState!!.close() })
    }
}
