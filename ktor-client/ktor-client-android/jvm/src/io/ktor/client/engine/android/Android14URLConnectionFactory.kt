/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.net.http.HttpEngine
import android.net.http.ProxyOptions
import android.util.Pair
import kotlinx.atomicfu.atomic
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

// @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
internal class AndroidNetHttpEngineFactory(private val config: AndroidEngineConfig) : URLConnectionFactory {
    private val engineDelegate = lazy { buildEngine() }
    private val engine by engineDelegate
    private val closed = atomic(false)

    private fun buildEngine(): HttpEngine {
        val ctx = checkNotNull(config.context) {
            "AndroidEngineConfig.context must be set when using HttpEngine; prefer applicationContext."
        }
        return HttpEngine.Builder(ctx)
            .apply {
                config.proxy?.let { ProxyHelper.setupProxy(this, it) }
                config.httpEngineConfig?.invoke(this)
            }
            .build()
    }

    override operator fun invoke(urlString: String): HttpURLConnection {
        return engine.openConnection(URI.create(urlString).toURL()) as HttpURLConnection
    }

    override fun close() {
        if (closed.compareAndSet(false, true) && engineDelegate.isInitialized()) {
            engine.shutdown()
        }
    }

    // @RequiresExtension(extension = Build.VERSION_CODES.S, version = 22)
    private object ProxyHelper {
        fun setupProxy(builder: HttpEngine.Builder, proxy: Proxy) {
            val address = proxy.address() as? InetSocketAddress ?: return
            if (proxy.type() != Proxy.Type.HTTP) return

            val httpProxy = android.net.http.Proxy.createHttpProxy(
                android.net.http.Proxy.SCHEME_HTTP,
                address.hostString,
                address.port,
                { it.run() },
                object : android.net.http.Proxy.HttpConnectCallback {
                    override fun onBeforeRequest(request: android.net.http.Proxy.HttpConnectCallback.Request) {
                        request.use { it.proceed(emptyList()) }
                    }

                    override fun onResponseReceived(
                        responseHeaders: List<Pair<String, String>>,
                        statusCode: Int
                    ): Int = android.net.http.Proxy.HttpConnectCallback.RESPONSE_ACTION_PROCEED
                }
            )
            builder.setProxyOptions(
                ProxyOptions.fromProxyList(
                    listOf(httpProxy),
                    ProxyOptions.ALL_PROXIES_FAILED_BEHAVIOR_DISALLOW_DIRECT
                )
            )
        }
    }
}
