package io.ktor.client.engine.curl

import kotlin.native.concurrent.*

internal class CurlProcessor : WorkerProcessor<CurlRequest, CurlResponse>() {

    fun start() {
        worker.execute(TransferMode.SAFE, { Unit }) {
            if (curlState == null)
                curlState = CurlState()
            else throw CurlEngineCreationException("An attempt to initialize curl twice.")
        }
    }

    fun requestJob(request: CurlRequest) {
        pendingFutures += worker.execute(TransferMode.SAFE, { request.freeze() }, ::curlUpdate)
    }

    fun close() {
        worker.execute(TransferMode.SAFE, { Unit }) { curlState?.close() }
    }

    companion object {
        @ThreadLocal
        private var curlState: CurlState? = null

        // Only set in curl thread
        private fun curlUpdate(request: CurlRequest): CurlResponse {
            request.newRequests.forEach {
                curlState!!.setupEasyHandle(it)
            }

            val readyResponses = curlState!!.singleIteration(100)

            return CurlResponse(readyResponses, request.listenerKey).freeze()
        }
    }
}
