package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.WinHttpIllegalStateException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import platform.windows.ERROR_INSUFFICIENT_BUFFER
import platform.windows.GetLastError
import winhttp.*

internal class WinHttpContext(
    private val hRequest: COpaquePointer,
    private val asyncWorkingMode: Boolean
) : DisposableHandle {
    private val reference: StableRef<WinHttpContext> = StableRef.create(this)

    private var sendRequestResult = CompletableDeferred<Unit>()
    private var writeDataResult = CompletableDeferred<Unit>()
    private var receiveResponseResult = CompletableDeferred<WinHttpResponseData>()
    private var queryDataAvailableResult = CompletableDeferred<Long>()
    private var readDataResult = CompletableDeferred<Int>()
    private var stage = Stage.SendRequest
    private var disposed = atomic(false)

    val isDisposed: Boolean
        get() = disposed.value

    fun reject(error: String) {
        dispose()

        val exception = WinHttpIllegalStateException(error)
        if (!asyncWorkingMode) {
            throw exception
        }

        when (stage) {
            Stage.SendRequest -> sendRequestResult.completeExceptionally(exception)
            Stage.WriteData -> writeDataResult.completeExceptionally(exception)
            Stage.ReceiveResponse -> receiveResponseResult.completeExceptionally(exception)
            Stage.QueryDataAvailable -> queryDataAvailableResult.completeExceptionally(exception)
            Stage.ReadData -> readDataResult.completeExceptionally(exception)
        }
    }

    fun enableHttp2Protocol() {
        memScoped {
            val flags = alloc<UIntVar> {
                value = WINHTTP_PROTOCOL_FLAG_HTTP2.convert()
            }
            WinHttpSetOption(hRequest, WINHTTP_OPTION_ENABLE_HTTP_PROTOCOL, flags.ptr, UINT_SIZE)
        }
    }

    fun sendRequest() {
        checkWorkingMode(false)

        if (WinHttpSendRequest(hRequest, null, 0, null, 0, 0, 0) == 0) {
            throw WinHttpIllegalStateException("Unable to send request: ${GetHResultFromLastError()}")
        }
    }

    fun sendRequestAsync(): Deferred<Unit> {
        checkWorkingMode(true)

        // Set status callback
        val function = staticCFunction(::statusCallback)
        if (WinHttpSetStatusCallback(hRequest, function, WINHTTP_CALLBACK_FLAG_ALL_COMPLETIONS, 0) != null) {
            throw WinHttpIllegalStateException("Request callback already exists")
        }

        // Send request
        val reference = reference.asCPointer().rawValue.toLong().convert<ULong>()
        if (WinHttpSendRequest(hRequest, null, 0, null, 0, 0, reference) == 0) {
            throw WinHttpIllegalStateException("Unable to send request: ${GetHResultFromLastError()}")
        }

        stage = Stage.SendRequest
        sendRequestResult = CompletableDeferred()

        return sendRequestResult
    }

    fun onSendRequestComplete() {
        sendRequestResult.complete(Unit)
    }

    fun writeData(body: Pinned<ByteArray>) {
        checkWorkingMode(false)

        // Write request data
        if (WinHttpWriteData(hRequest, body.addressOf(0), body.get().size.convert(), null) == 0) {
            throw WinHttpIllegalStateException("Unable to write request data: ${GetHResultFromLastError()}")
        }
    }

    fun writeDataAsync(body: Pinned<ByteArray>): Deferred<Unit> {
        checkWorkingMode(true)

        // Write request data
        if (WinHttpWriteData(hRequest, body.addressOf(0), body.get().size.convert(), null) == 0) {
            reject("Unable to write request data: ${GetHResultFromLastError()}")
        }

        stage = Stage.WriteData
        writeDataResult = CompletableDeferred()

        return writeDataResult
    }

    fun onWriteDataComplete() {
        writeDataResult.complete(Unit)
    }

    fun receiveResponse(): WinHttpResponseData {
        checkWorkingMode(false)

        if (WinHttpReceiveResponse(hRequest, null) == 0) {
            throw WinHttpIllegalStateException("Unable to receive response: ${GetHResultFromLastError()}")
        }

        return getResponseData()
    }

    fun receiveResponseAsync(): Deferred<WinHttpResponseData> {
        checkWorkingMode(true)

        if (WinHttpReceiveResponse(hRequest, null) == 0) {
            throw WinHttpIllegalStateException("Unable to receive response: ${GetHResultFromLastError()}")
        }

        stage = Stage.ReceiveResponse
        receiveResponseResult = CompletableDeferred()

        return receiveResponseResult
    }

    fun onReceiveResponse() {
        try {
            receiveResponseResult.complete(getResponseData())
        } catch (e: Throwable) {
            receiveResponseResult.completeExceptionally(e)
        }
    }

    fun queryDataAvailable(): Long {
        checkWorkingMode(false)

        return memScoped {
            val numberOfBytesAvailable = alloc<UIntVar>()

            if (WinHttpQueryDataAvailable(hRequest, numberOfBytesAvailable.ptr) == 0) {
                throw WinHttpIllegalStateException("Unable to query data length: ${GetHResultFromLastError()}")
            }

            numberOfBytesAvailable.value.convert()
        }
    }

    fun queryDataAvailableAsync(): Deferred<Long> {
        checkWorkingMode(true)

        if (WinHttpQueryDataAvailable(hRequest, null) == 0) {
            throw WinHttpIllegalStateException("Unable to query data length: ${GetHResultFromLastError()}")
        }

        stage = Stage.QueryDataAvailable
        queryDataAvailableResult = CompletableDeferred()

        return queryDataAvailableResult
    }

    fun onQueryDataAvailable(size: Long) {
        queryDataAvailableResult.complete(size)
    }

    fun readData(buffer: Pinned<ByteArray>): Int {
        checkWorkingMode(false)

        return memScoped {
            val numberOfBytesRead = alloc<UIntVar>()

            if (WinHttpReadData(
                    hRequest,
                    buffer.addressOf(0),
                    buffer.get().size.convert(),
                    numberOfBytesRead.ptr
                ) == 0
            ) {
                throw WinHttpIllegalStateException("Unable to read response data: ${GetHResultFromLastError()}.")
            }

            numberOfBytesRead.value.convert()
        }
    }

    fun readDataAsync(buffer: Pinned<ByteArray>): Deferred<Int> {
        checkWorkingMode(true)

        if (WinHttpReadData(hRequest, buffer.addressOf(0), buffer.get().size.convert(), null) == 0) {
            throw WinHttpIllegalStateException("Unable to read response data: ${GetHResultFromLastError()}.")
        }

        stage = Stage.ReadData
        readDataResult = CompletableDeferred()

        return readDataResult
    }


    fun onReadComplete(size: Int) {
        readDataResult.complete(size)
    }

    private fun getLength(dwSize: UIntVar) = (dwSize.value / ShortVar.size.convert()).convert<Int>()

    private fun getResponseData(): WinHttpResponseData = memScoped {
        val dwStatusCode = alloc<UIntVar>()
        val dwSize = alloc<UIntVar> {
            value = UINT_SIZE
        }

        // Get status code
        val statusCodeFlags = WINHTTP_QUERY_STATUS_CODE or WINHTTP_QUERY_FLAG_NUMBER
        if (WinHttpQueryHeaders(
                hRequest,
                statusCodeFlags.convert(),
                null,
                dwStatusCode.ptr,
                dwSize.ptr,
                null
            ) == 0
        ) {
            throw WinHttpIllegalStateException("Unable to query status code: ${GetHResultFromLastError()}")
        }

        val statusCode = dwStatusCode.value.convert<Int>()
        val httpVersion = if (isResponseHttp2()) {
            "HTTP/2.0"
        } else {
            getHeader(WINHTTP_QUERY_VERSION) ?: "HTTP/1.1"
        }
        val headers = getHeader(WINHTTP_QUERY_RAW_HEADERS_CRLF) ?: ""

        WinHttpResponseData(statusCode, httpVersion, headers)
    }

    private fun getHeader(headerId: Int): String? = memScoped {
        val dwSize = alloc<UIntVar>()

        // Get headers length
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, null, dwSize.ptr, null) == 0) {
            if (GetLastError() != ERROR_INSUFFICIENT_BUFFER.convert<UInt>()) {
                throw WinHttpIllegalStateException("Unable to query response headers length: ${GetHResultFromLastError()}")
            }
        }

        // Read headers into buffer
        val buffer = allocArray<ShortVar>(getLength(dwSize) + 1)
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, buffer, dwSize.ptr, null) == 0) {
            throw WinHttpIllegalStateException("Unable to query response headers: ${GetHResultFromLastError()}")
        }

        String(CharArray(getLength(dwSize)) {
            buffer[it].toChar()
        })
    }

    private fun isResponseHttp2(): Boolean {
        memScoped {
            val flags = alloc<UIntVar>()
            val dwSize = alloc<UIntVar> {
                value = UINT_SIZE
            }
            if (WinHttpQueryOption(hRequest, WINHTTP_OPTION_HTTP_PROTOCOL_USED, flags.ptr, dwSize.ptr) != 0) {
                if ((flags.value.convert<Int>() and WINHTTP_PROTOCOL_FLAG_HTTP2) != 0) {
                    return true
                }
            }
        }
        return false
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return
        reference.dispose()
    }

    private enum class Stage {
        SendRequest,
        WriteData,
        ReceiveResponse,
        QueryDataAvailable,
        ReadData
    }

    private fun checkWorkingMode(asyncCall: Boolean) {
        if (asyncWorkingMode != asyncCall) {
            val errorMessage = StringBuilder("Unable to execute in ").apply {
                if (asyncWorkingMode) this.append("a")
                this.append("synchronous mode")
            }.toString()
            throw WinHttpIllegalStateException(errorMessage)
        }
    }

    companion object {
        private const val WINHTTP_OPTION_ENABLE_HTTP_PROTOCOL = 133u
        private const val WINHTTP_OPTION_HTTP_PROTOCOL_USED = 134u
        private const val WINHTTP_PROTOCOL_FLAG_HTTP2 = 0x1

        private val UINT_SIZE: UInt = sizeOf<UIntVar>().convert()
    }
}
