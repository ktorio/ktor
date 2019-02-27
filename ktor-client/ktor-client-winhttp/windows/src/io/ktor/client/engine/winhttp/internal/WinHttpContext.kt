package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.WinHttpIllegalStateException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import platform.windows.*
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
        if (!asyncWorkingMode) return

        dispose()

        val exception = WinHttpIllegalStateException(error)

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
        sendRequestInternal()
    }

    fun sendRequestAsync(): Deferred<Unit> {
        checkWorkingMode(true)

        stage = Stage.SendRequest
        sendRequestResult = CompletableDeferred()

        sendRequestInternal()

        return sendRequestResult
    }

    private fun sendRequestInternal() {
        // Set status callback
        val function = staticCFunction(::statusCallback)
        val notifications = if (asyncWorkingMode) {
            WINHTTP_CALLBACK_STATUS_SECURE_FAILURE or WINHTTP_CALLBACK_STATUS_SECURE_FAILURE
        } else WINHTTP_CALLBACK_STATUS_SECURE_FAILURE

        if (WinHttpSetStatusCallback(hRequest, function, notifications.convert(), 0) != null) {
            throw createWinHttpError("Unable to set request callback")
        }

        // Send request
        val reference = reference.asCPointer().rawValue.toLong().convert<ULong>()
        if (WinHttpSendRequest(hRequest, null, 0, null, 0, 0, reference) == 0) {
            throw createWinHttpError("Unable to send request")
        }
    }

    fun onSendRequestComplete() {
        sendRequestResult.complete(Unit)
    }

    fun writeData(body: Pinned<ByteArray>) {
        checkWorkingMode(false)
        writeDataInternal(body)
    }

    fun writeDataAsync(body: Pinned<ByteArray>): Deferred<Unit> {
        checkWorkingMode(true)

        stage = Stage.WriteData
        writeDataResult = CompletableDeferred()

        writeDataInternal(body)

        return writeDataResult
    }

    private fun writeDataInternal(body: Pinned<ByteArray>) {
        // Write request data
        if (WinHttpWriteData(hRequest, body.addressOf(0), body.get().size.convert(), null) == 0) {
            throw createWinHttpError("Unable to write request data")
        }
    }

    fun onWriteDataComplete() {
        writeDataResult.complete(Unit)
    }

    fun receiveResponse(): WinHttpResponseData {
        checkWorkingMode(false)

        receiveResponseInternal()

        return getResponseData()
    }

    fun receiveResponseAsync(): Deferred<WinHttpResponseData> {
        checkWorkingMode(true)

        stage = Stage.ReceiveResponse
        receiveResponseResult = CompletableDeferred()

        receiveResponseInternal()

        return receiveResponseResult
    }

    private fun receiveResponseInternal() {
        if (WinHttpReceiveResponse(hRequest, null) == 0) {
            throw createWinHttpError("Unable to receive response")
        }
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
                throw createWinHttpError("Unable to query data length")
            }

            numberOfBytesAvailable.value.convert()
        }
    }

    fun queryDataAvailableAsync(): Deferred<Long> {
        checkWorkingMode(true)

        if (WinHttpQueryDataAvailable(hRequest, null) == 0) {
            throw createWinHttpError("Unable to query data length")
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
                throw createWinHttpError("Unable to read response data")
            }

            numberOfBytesRead.value.convert()
        }
    }

    fun readDataAsync(buffer: Pinned<ByteArray>): Deferred<Int> {
        checkWorkingMode(true)

        if (WinHttpReadData(hRequest, buffer.addressOf(0), buffer.get().size.convert(), null) == 0) {
            throw createWinHttpError("Unable to read response data")
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
            throw createWinHttpError("Unable to query status code")
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
            val errorCode = GetLastError()
            if (errorCode != ERROR_INSUFFICIENT_BUFFER.convert<UInt>()) {
                throw createWinHttpError("Unable to query response headers length")
            }
        }

        // Read headers into buffer
        val buffer = allocArray<ShortVar>(getLength(dwSize) + 1)
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, buffer, dwSize.ptr, null) == 0) {
            throw createWinHttpError("Unable to query response headers")
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
        WinHttpSetStatusCallback(hRequest, null, 0, 0)
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
