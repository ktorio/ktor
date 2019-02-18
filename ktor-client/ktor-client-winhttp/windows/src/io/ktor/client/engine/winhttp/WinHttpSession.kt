package io.ktor.client.engine.winhttp

import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.COpaquePointer
import kotlinx.coroutines.DisposableHandle
import winhttp.*

internal class WinHttpSession : DisposableHandle {

    private var hSession: COpaquePointer = WinHttpOpen(
        null, // User agent will be set in request headers
        WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
        null, null,
        WINHTTP_FLAG_ASYNC
    ) ?: throw WinHttpIllegalStateException("Unable to create session")
    private val disposed = atomic(false)

    fun setTimeouts(resolveTimeout: Int, connectTimeout: Int, sendTimeout: Int, receiveTimeout: Int) {
        if (WinHttpSetTimeouts(hSession, resolveTimeout, connectTimeout, sendTimeout, receiveTimeout) == 0) {
            throw WinHttpIllegalStateException("Unable to set session timeouts")
        }
    }

    fun createRequest(method: HttpMethod, url: Url): WinHttpRequest {
        return WinHttpRequest(hSession, method, url)
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return
        WinHttpCloseHandle(hSession)
    }
}
