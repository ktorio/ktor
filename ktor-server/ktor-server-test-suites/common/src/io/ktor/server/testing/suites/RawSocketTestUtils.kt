/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlin.time.Duration.Companion.minutes

internal suspend fun <T> withRawSocket(port: Int, block: suspend Connection.() -> T): T {
    SelectorManager().use { selector ->
        return aSocket(selector).tcp().connect("127.0.0.1", port) {
            noDelay = true
            socketTimeout = 4.minutes.inWholeMilliseconds
        }.use { socket ->
            val connection = socket.connection()
            try {
                block(connection)
            } finally {
                connection.output.flushAndClose()
            }
        }
    }
}

internal suspend fun ByteReadChannel.readHttpLine(): String {
    val line = StringBuilder()
    while (true) {
        when (val byte = readByte().toInt()) {
            -1, '\n'.code -> return line.toString()
            '\r'.code -> Unit
            else -> line.append(byte.toChar())
        }
    }
}
