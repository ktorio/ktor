package io.ktor.client.engine.curl

// This file knows nothing about ktor. Let's keep ti this way.
import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import platform.posix.*
import libcurl.*
import libcurl.CURLMsg.*

// This class is frozen to pass data between the curl driver and curl worker threads.
internal class CurlRequestData(
    val url: String,
    val method: String,
    val headers: CPointer<curl_slist>?,
    val content: ByteArray?
    // val attributes: Attributes
)
// This class is frozen to pass data between the curl worker and curl driver threads.
internal class CurlResponseData(
    val request: CurlRequestData,
    val chunks: MutableList<ByteArray> = mutableListOf(),
    val headers: MutableList<ByteArray> = mutableListOf()
) {
    /* lateinit */ var status: Int = 0
    /* lateinit */ var version: UInt = 0u
}

// The whole this class is executed in the worker.
internal class CurlState(val existingMultiHandle: COpaquePointer? = null) {
    private val multiHandle = existingMultiHandle ?: setupMultiHandle()

    fun setupMultiHandle(): COpaquePointer? {
        val handle = curl_multi_init() ?: error("Could not initialilze libcurl multi handle")

        return handle
    }

    fun close() {
        // Make sure all easy handles have been removed from the multi handle
        curl_multi_cleanup(multiHandle)
            .code()
    }

    fun setupUploadContent(easyHandle: COpaquePointer, content: ByteArray?) {
        if (content == null) return

        val stream = SimpleByteArrayStream(content)

        // The stable ref is disposed by the callback.
        easyHandle.apply {
            option(CURLOPT_READDATA, stream.stableCPointer())
            option(CURLOPT_READFUNCTION, staticCFunction(::readCallback))
            option(CURLOPT_INFILESIZE_LARGE, content.size)
        }
    }

    fun setupMethod(easyHandle: COpaquePointer, method: String) = when (method) {
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
        val easyHandle = curl_easy_init() ?: error("Could not initialize an easy handle")

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

    fun readResponseDataFromEasyHandle(easyHandle: COpaquePointer): CurlResponseData = memScoped {

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

    fun pickCompletedTransfers(): Set<CurlResponseData> {
        val responseDataList = mutableSetOf<CurlResponseData>()
        memScoped {
            do {
                val messagesLeft = alloc<IntVar>()
                val messagePtr = curl_multi_info_read(multiHandle, messagesLeft.ptr)
                val message = messagePtr?.pointed

                if (message == null) continue
                if (message.msg == CURLMSG.CURLMSG_DONE) {
                    val easyHandle = message.easy_handle ?: error("Got a null easy handle from the message")
                    responseDataList += readResponseDataFromEasyHandle(easyHandle)
                } else {
                    error("Unexpected curl message: $message.msg")
                }
            } while (messagesLeft.value != 0)
        }
        return responseDataList
    }

    fun singleIteration(timeoutMillisec: Int): Set<CurlResponseData> {
        memScoped {
            val transfersRunning = alloc<IntVar>()
            val activeFds = alloc<IntVar>()
            var repeats = 0

            // This basicly follows the curl_multi_wait man page.
            do {
                curl_multi_perform(multiHandle, transfersRunning.ptr)
                    .code() // TODO: may be do something less damaging here.

                curl_multi_wait(multiHandle, null, 0, timeoutMillisec, activeFds.ptr)
                    .code()

                /* 'activeFds.value' being zero means either a timeout or no file descriptors to
                   wait for. Try timeout on first occurrence, then assume no file
                   descriptors and no file descriptors to wait for means wait for [timeoutMillisec]
                   milliseconds. */

                if (activeFds.value == 0) {
                    repeats++; /* count number of repeated zero numfds */
                    if (repeats > 1) {
                        return emptySet()
                    }
                } else
                    repeats = 0;


            } while (transfersRunning.value != 0)
        }
        return pickCompletedTransfers()
    }
}
