/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.engine.mock.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import io.ktor.util.logging.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class JvmLoggingTest {
    @Test
    fun testLoggingWithForms() = testWithEngine(MockEngine) {
        val packetLogger = PacketLogger()

        config {
            engine {
                addHandler { request ->
                    val lines1 = request.body.toByteReadPacket().lines()
                    val body = lines1.joinToString("\n")
                    val lines = packetLogger.buildLog().lines()
                    val log = lines
                        .drop(4)
                        .dropLast(3)
                        .joinToString("\n")

                    assertEquals(log, body)
                    respondOk()
                }
            }

            install(Logging) {
                level = LogLevel.BODY
                logger = logger(packetLogger)
            }
        }

        test { client ->
            client.submitFormWithBinaryData<Unit>(formData = formData {
                append("name", "sunny day")
                append("image", "image.jpg") {
                    writeText("image-content")
                }
            })
        }
    }
}

private class PacketLogger : Appender {
    private val packet = BytePacketBuilder()

    override fun append(record: LogRecord) {
        packet.writeText("${record.text}\n")
    }

    override fun flush() {
    }

    fun buildLog(): ByteReadPacket = packet.build()
}

private fun ByteReadPacket.lines(): List<String> = mutableListOf<String>().apply {
    while (isNotEmpty) {
        this += readUTF8Line() ?: break
    }
}
