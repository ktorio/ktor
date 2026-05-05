/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import kotlinx.coroutines.*
import java.net.InetAddress

/**
 * Returns a [CIOEngineConfig.dnsResolver] backed by `java.net.InetAddress.getAllByName`,
 * dispatched on [dispatcher] and wrapped in [runInterruptible] so cancellation/`withTimeout`
 * actually unblocks the resolver thread.
 *
 * The underlying JVM call is still a blocking system call, but it is moved off the calling
 * coroutine and is interruptible — addressing the timeout-control complaint of KTOR-462 / #1678
 * while preserving native OS resolver behavior (`/etc/hosts`, mDNS, search domains, custom NSS).
 *
 * ```kotlin
 * HttpClient(CIO) {
 *     engine { dnsResolver = JvmDnsResolver() }
 *     install(HttpTimeout) { connectTimeoutMillis = 3000 }
 * }
 * ```
 *
 * For a fully non-blocking resolver that bypasses the OS name service, see [CioDnsResolver].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.JvmDnsResolver)
 */
@Suppress("FunctionName")
public fun JvmDnsResolver(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): suspend (hostname: String) -> List<String> = { hostname ->
    withContext(dispatcher) {
        runInterruptible {
            InetAddress.getAllByName(hostname).map { it.hostAddress }
        }
    }
}
