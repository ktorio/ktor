package io.ktor.client.engine.curl

// This file knows nothing about ktor. Let's keep ti this way.
import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import platform.posix.*
import libcurl.*
import libcurl.CURLMsg.*

// The whole this class is executed in the worker.
internal class CurlState(val existingMultiHandle: MultiHandle? = null) {
    private val multiHandle: MultiHandle = existingMultiHandle ?: setupMultiHandle()

    private fun setupMultiHandle(): MultiHandle
        = curl_multi_init() ?: throw CurlEngineCreationException("Could not initialilze libcurl multi handle")

    fun close() {
        // Make sure all easy handles have been removed from the multi handle
        curl_multi_cleanup(multiHandle)
            .code()
    }

    private fun setupUploadContent(easyHandle: EasyHandle, content: ByteArray?) {
        if (content == null) return

        val stream = SimpleByteArrayStream(content)

        // The stable ref is disposed by the callback.
        easyHandle.apply {
            option(CURLOPT_READDATA, stream.stableCPointer())
            option(CURLOPT_READFUNCTION, staticCFunction(::readCallback))
            option(CURLOPT_INFILESIZE_LARGE, content.size)
        }
    }

    private fun setupMethod(easyHandle: EasyHandle, method: String) = when (method) {
        "GET"
        -> easyHandle.option(CURLOPT_HTTPGET, 1L)
        "PUT"
        -> easyHandle.option(CURLOPT_UPLOAD, 1L)
        "POST"
        -> easyHandle.option(CURLOPT_POST, 1L)
        "HEAD"
        -> easyHandle.option(CURLOPT_NOBODY, 1L)
        else
        -> easyHandle.option(CURLOPT_CUSTOMREQUEST, method)
    }

    fun setupEasyHandle(request: CurlRequestData) {
        val easyHandle = curl_easy_init() ?: throw CurlIllegalStateException("Could not initialize an easy handle")

        val responseData = CurlResponseData(request, mutableListOf(), mutableListOf())
        val responseDataRef = responseData.stableCPointer()

        setupMethod(easyHandle, request.method)

        setupUploadContent(easyHandle, request.content)

        easyHandle.apply {
            option(CURLOPT_URL, request.url)
            option(CURLOPT_HTTPHEADER, request.headers)
            option(CURLOPT_HEADERFUNCTION, staticCFunction(::headerCallback))
            option(CURLOPT_HEADERDATA, responseDataRef)
            option(CURLOPT_WRITEFUNCTION, staticCFunction(::writeCallback))
            option(CURLOPT_WRITEDATA, responseDataRef)
            option(CURLOPT_PRIVATE, responseDataRef)
        }

        curl_multi_add_handle(multiHandle, easyHandle)
            .code()
    }

    private fun readResponseDataFromEasyHandle(easyHandle: EasyHandle): CurlResponseData = memScoped {

        val responseDataRef = alloc<COpaquePointerVar>()
        val httpProtocolVersion = alloc<LongVar>()
        val httpStatusCode = alloc<LongVar>()

        easyHandle.apply {
            getinfo(CURLINFO_PRIVATE, responseDataRef.ptr)
            getinfo(CURLINFO_RESPONSE_CODE, httpStatusCode.ptr)
            getinfo(CURLINFO_HTTP_VERSION, httpProtocolVersion.ptr)
        }

        val responseData = responseDataRef.value!!.fromCPointer<CurlResponseData>()
        responseData.status = httpStatusCode.value.toInt()
        responseData.version = httpProtocolVersion.value.toUInt()

        curl_multi_remove_handle(multiHandle, easyHandle)
        curl_easy_cleanup(easyHandle)

        responseData
    }

    private fun pickCompletedTransfers(): List<CurlResponseData> {
        val responseDataList = mutableListOf<CurlResponseData>()
        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed

                if (message == null) continue
                if (message.msg == CURLMSG.CURLMSG_DONE) {
                    val easyHandle = message.easy_handle ?: throw CurlIllegalStateException("Got a null easy handle from the message")
                    responseDataList += readResponseDataFromEasyHandle(easyHandle)
                } else {
                    throw CurlIllegalStateException("Unexpected curl message: $message.msg")
                }
            } while (messagesLeft.value != 0)
        }
        return responseDataList
    }

    fun singleIteration(timeoutMillisec: Int): List<CurlResponseData> {
        memScoped {
            val transfersRunning = alloc<IntVar>()
            val activeFileDescriptors = alloc<IntVar>()
            var repeats = 0

            // This basicly follows the curl_multi_wait man page.
            do {
                curl_multi_perform(multiHandle, transfersRunning.ptr)
                    .code() // TODO: may be do something less damaging here.

                curl_multi_wait(multiHandle, null, 0, timeoutMillisec, activeFileDescriptors.ptr)
                    .code()

                /* 'activeFileDescriptors.value' being zero means either a timeout or no file descriptors to
                   wait for. Try timeout on first occurrence, then assume no file
                   descriptors and no file descriptors to wait for means wait for [timeoutMillisec]
                   milliseconds. */

                if (activeFileDescriptors.value == 0) {
                    repeats++; /* count number of repeated zero numfds */
                    if (repeats > 1) {
                        return emptyList()
                    }
                } else
                    repeats = 0;


            } while (transfersRunning.value != 0)
        }
        return pickCompletedTransfers()
    }
}
