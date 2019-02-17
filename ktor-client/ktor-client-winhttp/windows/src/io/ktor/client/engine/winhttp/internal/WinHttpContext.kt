package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.WinHttpIllegalStateException
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.readBytes
import kotlinx.io.core.writeFully
import platform.windows.ERROR_INSUFFICIENT_BUFFER
import platform.windows.GetLastError
import winhttp.*
import kotlin.native.concurrent.freeze

internal class WinHttpContext : DisposableHandle {
    private val reference: StableRef<WinHttpContext> = StableRef.create(this)
    private val deferred = CompletableDeferred<WinHttpResponseData>()
    private var hSession: COpaquePointer? = null
    private var hConnect: COpaquePointer? = null
    private var hRequest: COpaquePointer? = null
    private var disposed = atomic(false)
    private val buffer: AtomicRef<ByteArray?> = atomic(null)
    private var requestBody: Pinned<ByteArray>? = null
    private var statusCode = 0
    private var httpVersion = "HTTP/1.1"
    private val headersBytes = BytePacketBuilder()
    private val bodyBytes = BytePacketBuilder()

    val isDisposed: Boolean
        get() = disposed.value

    fun complete() {
        val data = WinHttpResponseData(
            statusCode,
            httpVersion,
            headersBytes.build().readBytes(),
            bodyBytes.build().readBytes()
        ).freeze()

        dispose()

        println("Request has completed")

        deferred.complete(data)
    }

    fun reject(error: String) {
        println("Request has failed $error")
        dispose()
        val exception = WinHttpIllegalStateException(error)
        deferred.completeExceptionally(exception)
    }

    fun createSession() {
        hSession = WinHttpOpen(null, WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, null, null, WINHTTP_FLAG_ASYNC)
        if (hSession == null) {
            throw WinHttpIllegalStateException("Unable to create session")
        }
    }

    fun setTimeouts(resolveTimeout: Int, connectTimeout: Int, sendTimeout: Int, receiveTimeout: Int) {
        if (WinHttpSetTimeouts(hSession, resolveTimeout, connectTimeout, sendTimeout, receiveTimeout) == 0) {
            throw WinHttpIllegalStateException("Unable to set timeouts")
        }
    }

    fun createConnection(host: String, port: Int) {
        hConnect = WinHttpConnect(hSession, host, port.convert(), 0)
        if (hConnect == null) {
            throw WinHttpIllegalStateException("Unable to create connection")
        }
    }

    fun openRequest(method: String, path: String, isSecure: Boolean) {
        val secureCode = if (isSecure) WINHTTP_FLAG_SECURE.convert() else 0u
        hRequest = WinHttpOpenRequest(hConnect, method, path, null, null, null, secureCode)
        if (hRequest == null) {
            throw WinHttpIllegalStateException("Unable to open request")
        }
    }

    fun addRequestHeaders(requestHeaders: List<String>) {
        val headers = requestHeaders.joinToString("\n")
        if (WinHttpAddRequestHeaders(hRequest, headers, (-1).convert(), WINHTTP_ADDREQ_FLAG_ADD) == 0) {
            throw WinHttpIllegalStateException("Unable to add request header")
        }
    }

    fun addRequestBody(body: ByteArray) {
        requestBody = body.pin()
    }

    fun sendRequestAsync(): Deferred<WinHttpResponseData> {
        // Set status callback
        val function = staticCFunction(::statusCallback)
        if (WinHttpSetStatusCallback(hRequest, function, WINHTTP_CALLBACK_FLAG_ALL_COMPLETIONS, 0) != null) {
            throw WinHttpIllegalStateException("Callback already exists")
        }

        // Send request
        val reference = reference.asCPointer().rawValue.toLong().convert<ULong>()
        if (WinHttpSendRequest(hRequest, null, 0, null, 0, 0, reference) == 0) {
            throw WinHttpIllegalStateException("Unable to send request: ${GetHResultFromLastError()}")
        }

        return deferred
    }

    fun readHeaders(): Unit = memScoped {
        val dwStatusCode = alloc<UIntVar>()
        val dwSize = alloc<UIntVar> {
            value = sizeOf<UIntVar>().convert()
        }

        // Get status code
        val statusCodeFlags = WINHTTP_QUERY_STATUS_CODE or WINHTTP_QUERY_FLAG_NUMBER
        if (WinHttpQueryHeaders(hRequest, statusCodeFlags.convert(), null, dwStatusCode.ptr, dwSize.ptr, null) == 0) {
            reject("Unable to query status code: ${GetHResultFromLastError()}")
            return@memScoped
        }

        statusCode = dwStatusCode.value.convert()

        getHeader(WINHTTP_QUERY_VERSION)?.let {
            httpVersion = it
        }

        // Read all headers
        getHeader(WINHTTP_QUERY_RAW_HEADERS_CRLF)?.let {
            headersBytes.writeStringUtf8(it)
        }

        queryDataLength()
    }

    private fun getHeader(headerId: Int): String? = memScoped {
        val dwSize = alloc<UIntVar>()

        // Get headers length
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, null, dwSize.ptr, null) == 0) {
            if (GetLastError() != ERROR_INSUFFICIENT_BUFFER.convert<UInt>()) {
                reject("Unable to query headers length: ${GetHResultFromLastError()}")
                return@memScoped null
            }
        }

        // Read headers into buffer
        val buffer = allocArray<ShortVar>(getLength(dwSize) + 1)
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, buffer, dwSize.ptr, null) == 0) {
            reject("Unable to query headers: ${GetHResultFromLastError()}")
            return@memScoped null
        }

        String(CharArray(getLength(dwSize)) {
            buffer[it].toChar()
        })
    }

    fun readResponseData(size: Int): Unit = memScoped {
        println("Allocating buffer for $size bytes")
        val nativeBuffer = ByteArray(size)
        buffer.getAndSet(nativeBuffer)

        nativeBuffer.usePinned { buffer ->
            if (WinHttpReadData(hRequest, buffer.addressOf(0), size.convert(), null) == 0) {
                reject("Error ${GetHResultFromLastError()} in WinHttpReadData.")
                return@memScoped
            }
        }
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return

        hRequest?.let {
            WinHttpSetStatusCallback(it, null, 0, 0)
            WinHttpCloseHandle(it)
        }
        hConnect?.let {
            WinHttpCloseHandle(it)
        }
        hSession?.let {
            WinHttpCloseHandle(it)
        }

        buffer.getAndSet(null)

        requestBody?.unpin()
        requestBody = null

        reference.dispose()
    }

    fun onSendComplete() {
        requestBody?.let { pinned ->
            // Write request data
            if (WinHttpWriteData(hRequest, pinned.addressOf(0), pinned.get().size.convert(), null) == 0) {
                reject("Unable to write request data: ${GetHResultFromLastError()}")
            }
            return
        }

        receiveResponse()
    }

    fun onWriteComplete() {
        receiveResponse()
    }

    fun onReadComplete(length: Long) {
        println("Received $length bytes")
        val bodyPart = buffer.getAndSet(null)
        if (bodyPart == null) {
            reject("Response buffer is null")
        } else {
            bodyBytes.writeFully(bodyPart)
            queryDataLength()
        }
    }

    private fun getLength(dwSize: UIntVar) = (dwSize.value / ShortVar.size.convert()).convert<Int>()

    private fun queryDataLength(): Boolean {
        if (WinHttpQueryDataAvailable(hRequest, null) == 0) {
            reject("Unable to query data length: ${GetHResultFromLastError()}")
            return false
        }

        return true
    }

    private fun receiveResponse() {
        if (WinHttpReceiveResponse(hRequest, null) == 0) {
            reject("Unable to complete request: ${GetHResultFromLastError()}")
        }
    }
}
