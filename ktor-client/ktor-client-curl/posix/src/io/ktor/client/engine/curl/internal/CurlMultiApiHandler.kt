package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.*
import kotlinx.cinterop.*
import kotlinx.io.core.*
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

    private val multiHandle: MultiHandle = curl_multi_init()
        ?: throw CurlRuntimeException("Could not initialize curl multi handle")

    override fun close() {
        for ((handle, holders) in activeHandles) {
            holders.dispose()

            curl_multi_remove_handle(multiHandle, handle).verify()
            curl_easy_cleanup(handle)
        }

        activeHandles.clear()
        curl_multi_cleanup(multiHandle).verify()
    }

    fun scheduleRequest(request: CurlRequestData) {
        val easyHandle = curl_easy_init() ?: throw CurlIllegalStateException("Could not initialize an easy handle")

        val responseData = CurlResponseBuilder(request)
        val responseDataRef = responseData.asStablePointer()

        setupMethod(easyHandle, request.method)
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
        }

        curl_multi_add_handle(multiHandle, easyHandle).verify()
    }

    fun pollCompleted(millis: Int = 100): List<CurlResponseData> {
        memScoped {
            val transfersRunning = alloc<IntVar>()
            val activeTasks = alloc<IntVar>()
            var repeats = 0
            do {
                curl_multi_perform(multiHandle, transfersRunning.ptr).verify()
                curl_multi_wait(multiHandle, null, 0, millis, activeTasks.ptr).verify()

                repeats = if (activeTasks.value == 0) repeats + 1 else 0
            } while (repeats <= 1 && transfersRunning.value != 0)
        }

        return collectCompleted()
    }

    private fun setupMethod(easyHandle: EasyHandle, method: String) {
        easyHandle.apply {
            when (method) {
                "GET" -> option(CURLOPT_HTTPGET, 1L)
                "PUT" -> option(CURLOPT_PUT, 1L)
                "POST" -> option(CURLOPT_POST, 1L)
                "HEAD" -> option(CURLOPT_NOBODY, 1L)
                else -> option(CURLOPT_CUSTOMREQUEST, method)
            }
        }
    }

    private fun setupUploadContent(easyHandle: EasyHandle, content: ByteArray?): COpaquePointer? {
        if (content == null) return null

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

        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed ?: continue

                val easyHandle = message.easy_handle
                    ?: throw CurlIllegalStateException("Got a null easy handle from the message")

                try {
                    responseDataList += readResponseDataFromEasyHandle(message.msg, easyHandle)
                } finally {
                    activeHandles[easyHandle]!!.dispose()
                    activeHandles.remove(easyHandle)
                }
            } while (messagesLeft.value != 0)
        }

        return responseDataList
    }

    private fun readResponseDataFromEasyHandle(message: CURLMSG, easyHandle: EasyHandle): CurlResponseData = memScoped {
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
            curl_slist_free_all(responseBuilder.request.headers)

            if (message != CURLMSG.CURLMSG_DONE) {
                return CurlFail(
                    responseBuilder.request,
                    CurlIllegalStateException("Request ${responseBuilder.request} failed: $message")
                )
            }

            if (httpStatusCode.value == 0L) {
                return CurlFail(
                    responseBuilder.request,
                    CurlIllegalStateException("Connection failed for request: ${responseBuilder.request}")
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
            curl_multi_remove_handle(multiHandle, easyHandle).verify()
            curl_easy_cleanup(easyHandle)
        }
    }
}
