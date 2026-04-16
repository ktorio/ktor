/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.locks.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.io.readByteArray
import libcurl.*
import platform.posix.getenv
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
private class RequestHolder(
    val responseCompletable: CompletableDeferred<CurlSuccess>,
    val requestWrapper: StableRef<CurlRequestBodyData>,
    val responseWrapper: StableRef<CurlResponseBodyData>,
) {
    fun dispose() {
        requestWrapper.dispose()
        responseWrapper.dispose()
    }
}

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal class CurlMultiApiHandler : Closeable {
    private val activeHandles = mutableMapOf<EasyHandle, RequestHolder>()
    private val cancelledHandles = mutableSetOf<Pair<EasyHandle, Throwable>>()

    private val multiHandle: MultiHandle = curl_multi_init()
        ?: throw RuntimeException("Could not initialize curl multi handle")

    private val easyHandlesToUnpauseLock = SynchronizedObject()
    private val easyHandlesToUnpause = mutableListOf<EasyHandle>()

    override fun close() {
        if (activeHandles.isNotEmpty()) handleCompleted()
        for ((handle, holder) in activeHandles) {
            cleanupEasyHandle(handle)
            holder.dispose()
        }

        activeHandles.clear()
        curl_multi_cleanup(multiHandle).verify()
    }

    fun scheduleRequest(request: CurlRequestData, deferred: CompletableDeferred<CurlSuccess>): EasyHandle {
        val easyHandle = curl_easy_init()
            ?: error("Could not initialize an easy handle")

        val bodyStartedReceiving = CompletableDeferred<Unit>()
        val responseBody = if (request.isUpgradeRequest) {
            val wsConfig = request.attributes[WEBSOCKETS_KEY]
            CurlWebSocketResponseBody(
                easyHandle,
                wsConfig.channelsConfig.incoming
            )
        } else {
            CurlHttpResponseBody(request.executionContext) {
                unpauseEasyHandle(easyHandle)
            }
        }
        val responseData = CurlResponseBuilder(request, bodyStartedReceiving, responseBody)
        val responseDataRef = responseData.asStablePointer()
        val responseWrapper = responseBody.asStablePointer()

        bodyStartedReceiving.invokeOnCompletion {
            val result = collectSuccessResponse(easyHandle) ?: return@invokeOnCompletion
            activeHandles[easyHandle]!!.responseCompletable.complete(result)
        }

        setupMethod(easyHandle, request.method, request.contentLength)
        val requestWrapper = setupUploadContent(easyHandle, request)
        val requestHolder = RequestHolder(
            deferred,
            requestWrapper.asStableRef(),
            responseWrapper.asStableRef()
        )

        activeHandles[easyHandle] = requestHolder

        easyHandle.apply {
            option(CURLOPT_URL, request.url)
            option(CURLOPT_HTTPHEADER, request.headers)
            option(CURLOPT_HEADERFUNCTION, staticCFunction(::onHeadersReceived))
            option(CURLOPT_HEADERDATA, responseDataRef)
            option(CURLOPT_WRITEFUNCTION, staticCFunction(::onBodyChunkReceived))
            option(CURLOPT_WRITEDATA, responseWrapper)
            option(CURLOPT_PRIVATE, responseDataRef)
            option(CURLOPT_ACCEPT_ENCODING, "")
            request.connectTimeout?.let {
                if (it != HttpTimeoutConfig.INFINITE_TIMEOUT_MS) {
                    option(CURLOPT_CONNECTTIMEOUT_MS, request.connectTimeout)
                } else {
                    option(CURLOPT_CONNECTTIMEOUT_MS, Long.MAX_VALUE)
                }
            }

            request.proxy?.let { proxy ->
                option(CURLOPT_PROXY, fixProxyUrl(proxy.toString(), proxy.type))
                option(CURLOPT_SUPPRESS_CONNECT_HEADERS, 1L)
                if (request.forceProxyTunneling) {
                    option(CURLOPT_HTTPPROXYTUNNEL, 1L)
                }
            }

            if (!request.sslVerify) {
                option(CURLOPT_SSL_VERIFYPEER, 0L)
                option(CURLOPT_SSL_VERIFYHOST, 0L)
            }
            request.caPath?.let { option(CURLOPT_CAPATH, it) }
            request.caInfo?.let { option(CURLOPT_CAINFO, it) }
        }

        curl_multi_add_handle(multiHandle, easyHandle).verify()

        return easyHandle
    }

    private fun fixProxyUrl(url: String, proxyType: ProxyType): String {
        return if (proxyType == ProxyType.SOCKS) url.replaceFirst("socks://", "socks5://") else url
    }

    fun cancelRequest(easyHandle: EasyHandle, cause: Throwable) {
        cancelledHandles += Pair(easyHandle, cause)
    }

    fun perform(transfersRunning: IntVarOf<Int>) {
        if (activeHandles.isEmpty()) return

        synchronized(easyHandlesToUnpauseLock) {
            var handle = easyHandlesToUnpause.removeFirstOrNull()
            while (handle != null) {
                curl_easy_pause(handle, CURLPAUSE_CONT)
                handle = easyHandlesToUnpause.removeFirstOrNull()
            }
        }
        curl_multi_perform(multiHandle, transfersRunning.ptr).verify()
        if (transfersRunning.value != 0) {
            curl_multi_poll(multiHandle, null, 0.toUInt(), pollTimeout, null).verify()
        }
        if (transfersRunning.value < activeHandles.size) {
            handleCompleted()
        }
    }

    fun hasHandlers(): Boolean = activeHandles.isNotEmpty()

    private fun setupMethod(
        easyHandle: EasyHandle,
        method: String,
        size: Long
    ) {
        easyHandle.apply {
            when (method) {
                "GET" -> option(CURLOPT_HTTPGET, 1L)

                "PUT" -> {
                    option(CURLOPT_PUT, 1L)
                    option(CURLOPT_INFILESIZE_LARGE, size)
                }

                "POST" -> {
                    option(CURLOPT_POST, 1L)
                    option(CURLOPT_POSTFIELDSIZE_LARGE, size)
                }

                "HEAD" -> option(CURLOPT_NOBODY, 1L)

                else -> {
                    if (size > 0) {
                        option(CURLOPT_POST, 1L)
                        option(CURLOPT_POSTFIELDSIZE_LARGE, size)
                    }
                    option(CURLOPT_CUSTOMREQUEST, method)
                }
            }
        }
    }

    private fun setupUploadContent(easyHandle: EasyHandle, request: CurlRequestData): COpaquePointer {
        val requestPointer = CurlRequestBodyData(
            body = request.content,
            callContext = request.executionContext,
            onUnpause = {
                unpauseEasyHandle(easyHandle)
            }
        ).asStablePointer()

        easyHandle.apply {
            option(CURLOPT_READDATA, requestPointer)
            option(CURLOPT_READFUNCTION, staticCFunction(::onBodyChunkRequested))
        }
        return requestPointer
    }

    private fun handleCompleted() {
        for (cancellation in cancelledHandles) {
            val cancelled = processCancelledEasyHandle(cancellation.first, cancellation.second)
            val handler = activeHandles.remove(cancellation.first)!!
            handler.responseCompletable.completeExceptionally(cancelled.cause)
            handler.dispose()
        }
        cancelledHandles.clear()

        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed ?: continue

                val easyHandle = message.easy_handle
                    ?: error("Got a null easy handle from the message")

                try {
                    val result = processCompletedEasyHandle(message.msg, easyHandle, message.data.result)
                    val deferred = activeHandles[easyHandle]!!.responseCompletable
                    if (deferred.isCompleted) {
                        // already completed with partial response
                        continue
                    }
                    when (result) {
                        is CurlSuccess -> deferred.complete(result)
                        is CurlFail -> deferred.completeExceptionally(result.cause)
                    }
                } finally {
                    activeHandles.remove(easyHandle)!!.dispose()
                }
            } while (messagesLeft.value != 0)
        }
    }

    private fun processCancelledEasyHandle(easyHandle: EasyHandle, cause: Throwable): CurlFail = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            easyHandle.apply { getInfo(CURLINFO_PRIVATE, responseDataRef.ptr) }
            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                return CurlFail(cause)
            } finally {
                responseBuilder.responseBody.close(cause)
                responseBuilder.headersBytes.close()
            }
        } finally {
            cleanupEasyHandle(easyHandle)
        }
    }

    private fun processCompletedEasyHandle(
        message: CURLMSG?,
        easyHandle: EasyHandle,
        result: CURLcode
    ): CurlResponseData = memScoped {
        try {
            val responseDataRef = alloc<COpaquePointerVar>()
            val httpStatusCode = alloc<LongVar>()

            easyHandle.apply {
                getInfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
                getInfo(CURLINFO_PRIVATE, responseDataRef.ptr)
            }

            val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
            try {
                collectFailedResponse(message, responseBuilder.request, result, httpStatusCode.value)
                    ?: collectSuccessResponse(easyHandle)!!
            } finally {
                responseBuilder.responseBody.close()
                responseBuilder.headersBytes.close()
            }
        } finally {
            cleanupEasyHandle(easyHandle)
        }
    }

    private fun collectFailedResponse(
        message: CURLMSG?,
        request: CurlRequestData,
        result: CURLcode,
        httpStatusCode: Long
    ): CurlFail? {
        curl_slist_free_all(request.headers)

        if (message != CURLMSG.CURLMSG_DONE) {
            return CurlFail(
                IllegalStateException("Request $request failed: $message")
            )
        }

        if (httpStatusCode != 0L) {
            return null
        }

        if (result == CURLE_OPERATION_TIMEDOUT) {
            return CurlFail(ConnectTimeoutException(request.url, request.connectTimeout))
        }

        val errorMessage = curl_easy_strerror(result)?.toKStringFromUtf8()

        if (result == CURLE_PEER_FAILED_VERIFICATION) {
            return CurlFail(
                IllegalStateException(
                    "TLS verification failed for request: $request. Reason: $errorMessage"
                )
            )
        }

        return CurlFail(
            IllegalStateException("Connection failed for request: $request. Reason: $errorMessage")
        )
    }

    private fun collectSuccessResponse(easyHandle: EasyHandle): CurlSuccess? = memScoped {
        val responseDataRef = alloc<COpaquePointerVar>()
        val httpProtocolVersion = alloc<LongVar>()
        val httpStatusCode = alloc<LongVar>()

        easyHandle.apply {
            getInfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
            getInfo(CURLINFO_HTTP_VERSION, httpProtocolVersion.ptr)
            getInfo(CURLINFO_PRIVATE, responseDataRef.ptr)
        }

        if (httpStatusCode.value == 0L) {
            // if error happened, it will be handled in collectCompleted
            return@memScoped null
        }

        val responseBuilder = responseDataRef.value!!.fromCPointer<CurlResponseBuilder>()
        with(responseBuilder) {
            val headers = headersBytes.build().readByteArray()

            CurlSuccess(
                httpStatusCode.value.toInt(),
                httpProtocolVersion.value,
                headers,
                responseBody
            )
        }
    }

    fun wakeup() {
        curl_multi_wakeup(multiHandle)
    }

    fun sendWebSocketFrame(
        websocket: CurlWebSocketResponseBody,
        flags: Int,
        data: ByteArray,
        completionHandler: CompletableJob
    ) {
        try {
            trySendWebSocketFrame(websocket.easyHandle, flags, data)
            completionHandler.complete()
        } catch (cause: Throwable) {
            completionHandler.completeExceptionally(cause)
        }
    }

    private fun trySendWebSocketFrame(
        easyHandle: EasyHandle,
        flags: Int,
        data: ByteArray,
    ) = memScoped {
        var offset = 0
        val sent = alloc<size_tVar>()
        data.usePinned { pinned ->
            while (true) {
                val bufferStart = if (data.isNotEmpty()) pinned.addressOf(offset) else null
                val remaining = if (data.isNotEmpty()) data.size - offset else 0

                val status = curl_ws_send(
                    curl = easyHandle,
                    buffer = bufferStart,
                    buflen = remaining.convert(),
                    sent = sent.ptr,
                    fragsize = 0,
                    flags = flags.convert(),
                )

                when (status) {
                    CURLE_OK -> {
                        offset += sent.value.toInt()
                        if (data.isEmpty() || offset == data.size) break
                    }

                    // TODO: Handle CURLE_AGAIN
                    else -> status.verify()
                }
            }
        }
    }

    private fun unpauseEasyHandle(easyHandle: EasyHandle) {
        synchronized(easyHandlesToUnpauseLock) {
            easyHandlesToUnpause.add(easyHandle)
        }
        curl_multi_wakeup(multiHandle)
    }

    private fun cleanupEasyHandle(easyHandle: EasyHandle) {
        curl_multi_remove_handle(multiHandle, easyHandle).verify()
        curl_easy_cleanup(easyHandle)
    }

    private companion object {
        private const val DEFAULT_POLL_TIMEOUT_MS = 100
        val pollTimeout by lazy { getenv("KTOR_CURL_POLL_TIMEOUT")?.toKString()?.toInt() ?: DEFAULT_POLL_TIMEOUT_MS }
    }
}
