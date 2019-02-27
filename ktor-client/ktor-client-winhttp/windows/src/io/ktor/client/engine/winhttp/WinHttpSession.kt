package io.ktor.client.engine.winhttp

import io.ktor.client.engine.winhttp.internal.*
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.DisposableHandle
import winhttp.*

internal class WinHttpSession(private val asyncWorkingMode: Boolean) : DisposableHandle {

    private var hSession: COpaquePointer
    private val disposed = atomic(false)

    init {
        val workingMode = if (asyncWorkingMode) WINHTTP_FLAG_ASYNC else 0
        hSession = WinHttpOpen(
            null, // User agent will be set in request headers
            WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
            null, null,
            workingMode.convert()
        ) ?: throw createWinHttpError("Unable to create session")
    }

    fun setTimeouts(resolveTimeout: Int, connectTimeout: Int, sendTimeout: Int, receiveTimeout: Int) {
        if (WinHttpSetTimeouts(hSession, resolveTimeout, connectTimeout, sendTimeout, receiveTimeout) == 0) {
            throw createWinHttpError("Unable to set session timeouts")
        }
    }

    fun setSecurityProtocols(securityProtocols: WinHttpSecurityProtocol) {
        memScoped {
            val options = alloc<UIntVar> {
                value = securityProtocols.value.convert()
            }
            val dwSize = sizeOf<UIntVar>().convert<UInt>()
            WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS, options.ptr, dwSize)
        }
    }

    fun createRequest(method: HttpMethod, url: Url): WinHttpRequest {
        return WinHttpRequest(hSession, method, url, asyncWorkingMode)
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return
        WinHttpCloseHandle(hSession)
    }
}
