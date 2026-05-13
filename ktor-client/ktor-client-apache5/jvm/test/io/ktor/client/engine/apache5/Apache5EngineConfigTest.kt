/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.apache.hc.client5.http.DnsResolver
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class Apache5EngineConfigTest {

    @Test
    fun `dnsResolver config is invoked when set`() = runBlocking {
        val callCount = AtomicInteger()
        val resolver = object : DnsResolver {
            override fun resolve(host: String): Array<InetAddress> {
                callCount.incrementAndGet()
                return arrayOf(InetAddress.getByName("127.0.0.1"))
            }

            override fun resolveCanonicalHostname(host: String): String = "127.0.0.1"
        }

        val client = HttpClient(Apache5) {
            engine { dnsResolver = resolver }
        }

        try {
            runCatching { client.get("http://example.invalid:1/") }
        } finally {
            client.close()
        }

        assertTrue(callCount.get() > 0, "Custom DnsResolver should have been invoked")
    }
}
