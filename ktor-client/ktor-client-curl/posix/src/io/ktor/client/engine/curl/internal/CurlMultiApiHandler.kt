/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.*
import io.ktor.client.features.*
import kotlinx.cinterop.*
import io.ktor.utils.io.core.*
import libcurl.*

private class RequestHolders(
    private val requestBody: StableRef<ByteReadPacket>?,
    private val response: StableRef<CurlResponseBuilder>
) {
    fun dispose() {
        requestBody?.dispose()
        response.dispose()
    }
}

internal class CurlMultiApiHandler : Closeable {
    private val activeHandles: MutableMap<EasyHandle, RequestHolders> = mutableMapOf()

    private val cancelledHandles: MutableList<Pair<EasyHandle, Throwable>> = mutableListOf()

    private val multiHandle: MultiHandle = curl_multi_init()
        ?: @Suppress("DEPRECATION") throw CurlRuntimeException("Could not initialize curl multi handle")

    override fun close() {
        for ((handle, holders) in activeHandles) {
            holders.dispose()

            curl_multi_remove_handle(multiHandle, handle).verify()
            curl_easy_cleanup(handle)
        }

        activeHandles.clear()
        curl_multi_cleanup(multiHandle).verify()
    }

    fun scheduleRequest(request: CurlRequestData): EasyHandle {
        val easyHandle = curl_easy_init()
            ?: throw @Suppress("DEPRECATION") CurlIllegalStateException("Could not initialize an easy handle")

        val responseData = CurlResponseBuilder(request)
        val responseDataRef = responseData.asStablePointer()

        setupMethod(easyHandle, request.method, request.content.size)
        val contentHolder = setupUploadContent(easyHandle, request.content)

        activeHandles[easyHandle] = RequestHolders(contentHolder?.asStableRef(), responseDataRef.asStableRef())

        easyHandle.apply {
            option(CURLOPT_URL, request.url)
            option(CURLOPT_HTTPHEADER, request.headers)
            option(CURLOPT_HEADERFUNCTION, staticCFunction(::onHeadersReceived))
            option(CURLOPT_HEADERDATA, responseDataRef)
            option(CURLOPT_WRITEFUNCTION, staticCFunction(::onBodyChunkReceived))
            option(CURLOPT_WRITEDATA, responseDataRef)
            option(CURLOPT_PRIVATE, responseDataRef)
            option(CURLOPT_ACCEPT_ENCODING, "")
            request.connectTimeout?.let {
                if (it != HttpTimeout.INFINITE_TIMEOUT_MS) {
                    option(CURLOPT_CONNECTTIMEOUT_MS, request.connectTimeout)
                } else {
                    option(CURLOPT_CONNECTTIMEOUT_MS, Long.MAX_VALUE)
                }
            }

            request.proxy?.let { proxy ->
                option(CURLOPT_PROXY, proxy.toString())
            }
        }

        curl_multi_add_handle(multiHandle, easyHandle).verify()

        return easyHandle
    }

    internal fun cancelRequest(easyHandle: EasyHandle, cause: Throwable) {
        cancelledHandles += Pair(easyHandle, cause)
    }

    fun pollCompleted(millis: Int = 100): List<CurlResponseData> {
        memScoped {
            val transfersRunning = alloc<IntVar>()
            val activeTasks = alloc<IntVar>()
            var repeats = 0
            do {
                curl_multi_perform(multiHandle, transfersRunning.ptr).verify()
                curl_multi_wait(multiHandle, null, 0.toUInt(), millis, activeTasks.ptr).verify()

                repeats = if (activeTasks.value == 0) repeats + 1 else 0
            } while (repeats <= 1 && transfersRunning.value != 0)
        }

        return collectCompleted()
    }

    private fun setupMethod(
        easyHandle: EasyHandle,
        method: String,
        size: Int
    ) {
        easyHandle.apply {
            when (method) {
                "GET" -> option(CURLOPT_HTTPGET, 1L)
                "PUT" -> {
                    option(CURLOPT_PUT, 1L)
                }
                "POST" -> {
                    option(CURLOPT_POST, 1L)
                    option(CURLOPT_POSTFIELDSIZE, size)
                }
                "HEAD" -> option(CURLOPT_NOBODY, 1L)
                else -> option(CURLOPT_CUSTOMREQUEST, method)
            }
        }
    }

    private fun setupUploadContent(easyHandle: EasyHandle, content: ByteArray): COpaquePointer? {
        val stream = buildPacket { writeFully(content) }
        val requestPointer = stream.asStablePointer()

        easyHandle.apply {
            option(CURLOPT_READDATA, requestPointer)
            option(CURLOPT_READFUNCTION, staticCFunction(::onBodyChunkRequested))
            option(CURLOPT_INFILESIZE_LARGE, content.size)
        }

        return requestPointer
    }

    private fun collectCompleted(): List<CurlResponseData> {
        val responseDataList = mutableListOf<CurlResponseData>()

        for (cancellation in cancelledHandles) {
            responseDataList += processCancelledEasyHandle(cancellation.first, cancellation.second)
            activeHandles.remove(cancellation.first)!!.dispose()
        }
        cancelledHandles.clear()

        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed ?: continue

                val easyHandle = message.easy_handle
                    ?: @Suppress("DEPRECATION")
                    throw CurlIllegalStateException("Got a null easy handle from the message")

                try {
                    responseDataList += readResponseDataFromEasyHandle(message.msg, easyHandle, message.data.result)
                } finally {
                    activeHandles[easyHandle]!!.dispose()
                    activeHandles.remove(easyHandle)
                }
            } while (messagesLeft.value != 0)
        }

        return responseDataList
    }

    private fun processCancelledEasyHandle(easyHandle: EasyHandle, cause: Throwable): CurlFail = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            easyHandle.apply { getInfo(CURLINFO_PRIVATE, responseDataRef.ptr) }
            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                return CurlFail(responseBuilder.request, cause)
            } finally {
                responseBuilder.bodyBytes.release()
                responseBuilder.headersBytes.release()
            }
        } finally {
            curl_multi_remove_handle(multiHandle, easyHandle).verify()
            curl_easy_cleanup(easyHandle)
        }
    }

    private fun readResponseDataFromEasyHandle(
        message: CURLMSG?,
        easyHandle: EasyHandle,
        result: CURLcode
    ): CurlResponseData = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            val httpProtocolVersion = alloc<LongVar>()
            val httpStatusCode = alloc<LongVar>()

            easyHandle.apply {
                getInfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
//                getInfo(CURLINFO_HTTP_VERSION, httpProtocolVersion.ptr)
                getInfo(CURLINFO_PRIVATE, responseDataRef.ptr)
            }

            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                val request = responseBuilder.request
                curl_slist_free_all(request.headers)

                if (message != CURLMSG.CURLMSG_DONE) {
                    return CurlFail(
                        request,
                        @Suppress("DEPRECATION")
                        CurlIllegalStateException("Request $request failed: $message")
                    )
                }

                if (httpStatusCode.value == 0L) {
                    if (result == CURLE_OPERATION_TIMEDOUT) {
                        return CurlFail(
                            request, ConnectTimeoutException(request.url, request.connectTimeout)
                        )
                    }

                    return CurlFail(
                        request,
                        @Suppress("DEPRECATION")
                        CurlIllegalStateException("Connection failed for request: $request")
                    )
                }

                with(responseBuilder) {
                    val headers = headersBytes.build().readBytes()
                    val body = bodyBytes.build().readBytes()

                    CurlSuccess(
                        request,
                        httpStatusCode.value.toInt(), httpProtocolVersion.value.toUInt(),
                        headers, body
                    )
                }
            } finally {
                responseBuilder.bodyBytes.release()
                responseBuilder.headersBytes.release()
            }
        } finally {
            curl_multi_remove_handle(multiHandle, easyHandle).verify()
            curl_easy_cleanup(easyHandle)
        }
    }
}
