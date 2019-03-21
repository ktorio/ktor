package io.ktor.client.features.logging

import io.ktor.client.engine.mock.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import kotlinx.io.core.*
import kotlin.test.*


class CommonLoggingTest {
    @Test
    fun testLoggingWithForms() = clientTest(MockEngine) {
        val packetLogger = PacketLogger()

        config {
            engine {
                addHandler { request ->
                    val body = request.content.toByteReadPacket().lines().joinToString("\n")
                    val log = packetLogger.buildLog().lines()
                        .drop(4)
                        .dropLast(3)
                        .joinToString("\n")

                    assertEquals(log, body)
                    request.responseOk()
                }
            }

            install(Logging) {
                level = LogLevel.BODY
                logger = packetLogger
            }
        }

        test { client ->
            client.submitFormWithBinaryData<Unit>(formData = formData {
                append("name", "sunny day")
                append("image", "image.jpg") {
                    writeStringUtf8("image-content")
                }
            })
        }
    }
}

private class PacketLogger : Logger {
    private val packet = BytePacketBuilder()

    override fun log(message: String) {
        packet.writeStringUtf8("$message\n")
    }

    fun buildLog(): ByteReadPacket = packet.build()
}

private fun ByteReadPacket.lines(): List<String> = mutableListOf<String>().apply {
    while (isNotEmpty) {
        this += readUTF8Line() ?: break
    }
}
