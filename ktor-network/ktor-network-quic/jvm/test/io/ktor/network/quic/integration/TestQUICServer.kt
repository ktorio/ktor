/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.integration

import io.ktor.network.quic.sockets.*
import io.ktor.network.quic.streams.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.test.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.stream.*

class TestQUICServer : Closeable {
    private val serverAddress = InetSocketAddress("localhost", FreePorts.select())

    val port get() = serverAddress.port

    private val selector = SelectorManager()
    private val server: BoundQUICSocket = aSocket(selector).quic().bind(serverAddress) {
        certificatePath = certificate
        privateKeyPath = key
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            val stream = server.incoming.receive()
            stream.output.use {
                val input = stream.input.readText()

                it.writeText(input)
            }
        }
    }

    override fun close() {
        job.cancel()
        FreePorts.recycle(serverAddress.port)

        safeClose(server, selector)
    }

    private fun safeClose(vararg resources: Closeable) {
        var exception: Exception? = null
        for (i in resources) {
            try {
                i.close()
            } catch (e: Exception) {
                if (exception == null) {
                    exception = e
                }
            }
        }

        exception?.let { throw it }
    }

    companion object {
        private val resourcesPath = this::class.java
            .classLoader
            .resources("cert.pem")
            .collect(Collectors.toList())
            .single()
            .file
            .dropLast(8)

        private val certificate = "$resourcesPath/cert.pem"
        private val key = "$resourcesPath/key.pem"
    }
}
