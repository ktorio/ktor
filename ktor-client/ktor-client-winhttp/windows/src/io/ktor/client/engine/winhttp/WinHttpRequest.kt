package io.ktor.client.engine.winhttp

import io.ktor.client.engine.winhttp.internal.*
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.isSecure
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import winhttp.*

internal class WinHttpRequest(
    hSession: COpaquePointer,
    method: HttpMethod,
    url: Url,
    asyncWorkingMode: Boolean
) : DisposableHandle {

    private val hConnect: COpaquePointer
    private val hRequest: COpaquePointer
    private val context: WinHttpContext
    private val disposed = atomic(false)

    init {
        hConnect = WinHttpConnect(hSession, url.host, url.port.convert(), 0)
            ?: throw createWinHttpError("Unable to create connection")
        val secure = if (url.protocol.isSecure()) WINHTTP_FLAG_SECURE.convert() else 0u
        hRequest = WinHttpOpenRequest(hConnect, method.value, url.fullPath, null, null, null, secure)
            ?: throw createWinHttpError("Unable to open request")
        context = WinHttpContext(hRequest, asyncWorkingMode)
    }

    fun addHeaders(requestHeaders: List<String>) {
        val headers = requestHeaders.joinToString("\n")
        if (WinHttpAddRequestHeaders(hRequest, headers, (-1).convert(), WINHTTP_ADDREQ_FLAG_ADD) == 0) {
            throw createWinHttpError("Unable to add request headers")
        }
    }

    fun send() {
        context.sendRequest()
    }

    fun sendAsync(): Deferred<Unit> {
        return context.sendRequestAsync()
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return
        context.dispose()
        WinHttpCloseHandle(hRequest)
    }

    fun writeBody(body: Pinned<ByteArray>) {
        return context.writeData(body)
    }

    fun writeBodyAsync(body: Pinned<ByteArray>): Deferred<Unit> {
        return context.writeDataAsync(body)
    }

    fun readResponse(): WinHttpResponseData {
        return context.receiveResponse()
    }

    fun readResponseAsync(): Deferred<WinHttpResponseData> {
        return context.receiveResponseAsync()
    }

    fun queryDataAvailable(): Long {
        return context.queryDataAvailable()
    }

    fun queryDataAvailableAsync(): Deferred<Long> {
        return context.queryDataAvailableAsync()
    }

    fun readData(buffer: Pinned<ByteArray>): Int {
        return context.readData(buffer)
    }

    fun readDataAsync(buffer: Pinned<ByteArray>): Deferred<Int> {
        return context.readDataAsync(buffer)
    }

    fun enableHttp2Protocol() {
        context.enableHttp2Protocol()
    }
}
