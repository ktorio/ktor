/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import platform.winhttp.*

@OptIn(ExperimentalForeignApi::class)
internal class WinHttpSession(private val config: WinHttpClientEngineConfig) : Closeable {

    private var hSession: COpaquePointer
    private val closed = atomic(false)

    init {
        hSession = WinHttpOpen(
            WINHTTP_NO_USER_AGENT,
            WINHTTP_ACCESS_TYPE_DEFAULT_PROXY.convert(),
            WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS,
            WINHTTP_FLAG_ASYNC.convert()
        ) ?: throw getWinHttpException("Unable to create session")

        setSecurityProtocols(config.securityProtocols)
        hSession.setTimeouts(DEFAULT_CONNECT_TIMEOUT, DEFAULT_SOCKET_TIMEOUT)

        config.proxy?.let { proxy ->
            setProxy(proxy)
        }
    }

    fun createRequest(data: HttpRequestData): WinHttpRequest = WinHttpRequest(hSession, data, config)

    private fun setSecurityProtocols(protocol: WinHttpSecurityProtocol) = memScoped {
        val options = alloc<UIntVar> {
            value = protocol.value.convert()
        }
        val dwSize = sizeOf<UIntVar>().convert<UInt>()
        WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS.convert(), options.ptr, dwSize)
    }

    private fun setProxy(proxy: ProxyConfig) = memScoped {
        when (val type = proxy.type) {
            ProxyType.HTTP -> {
                val proxyInfo = alloc<WINHTTP_PROXY_INFO> {
                    dwAccessType = WINHTTP_ACCESS_TYPE_NAMED_PROXY.convert()
                    lpszProxy = proxy.url.toString().wcstr.ptr
                }

                if (WinHttpSetOption(
                        hSession,
                        WINHTTP_OPTION_PROXY.convert(),
                        proxyInfo.ptr,
                        sizeOf<WINHTTP_PROXY_INFO>().convert()
                    ) == 0
                ) {
                    throw getWinHttpException("Unable to set proxy")
                }
            }

            else -> throw IllegalStateException("Proxy of type $type is unsupported by WinHTTP engine.")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return

        WinHttpCloseHandle(hSession)
    }

    companion object {
        private val WINHTTP_NO_PROXY_NAME = null
        private val WINHTTP_NO_PROXY_BYPASS = null
        private val WINHTTP_NO_USER_AGENT = null
    }
}

internal const val DEFAULT_CONNECT_TIMEOUT = 60_000
internal const val DEFAULT_SOCKET_TIMEOUT = 30_000
private const val DEFAULT_RESOLVE_TIMEOUT = 10_000

/**
 * Sets timeouts on a session or request handle. Timeouts set on a request handle override the session ones.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun COpaquePointer.setTimeouts(connectTimeout: Int, socketTimeout: Int) {
    if (WinHttpSetTimeouts(this, DEFAULT_RESOLVE_TIMEOUT, connectTimeout, socketTimeout, socketTimeout) == 0) {
        throw getWinHttpException("Unable to set timeouts")
    }
}
