package io.ktor.http.cio

import io.ktor.http.*
import kotlinx.io.core.*
import java.nio.*

/**
 * Builds an HTTP request or response
 */
class RequestResponseBuilder {
    private val packet = BytePacketBuilder()

    /**
     * Append response status line
     */
    fun responseLine(version: CharSequence, status: Int, statusText: CharSequence) {
        packet.writeStringUtf8(version)
        packet.writeByte(SP)
        packet.writeStringUtf8(status.toString())
        packet.writeByte(SP)
        packet.writeStringUtf8(statusText)
        packet.writeByte(CR)
        packet.writeByte(LF)
    }

    /**
     * Append request line
     */
    fun requestLine(method: HttpMethod, uri: CharSequence, version: CharSequence) {
        packet.writeStringUtf8(method.value)
        packet.writeByte(SP)
        packet.writeStringUtf8(uri)
        packet.writeByte(SP)
        packet.writeStringUtf8(version)
        packet.writeByte(CR)
        packet.writeByte(LF)
    }

    /**
     * Append a line
     */
    fun line(line: CharSequence) {
        packet.append(line)
        packet.writeByte(CR)
        packet.writeByte(LF)
    }

    /**
     * Append raw bytes
     */
    fun bytes(content: ByteArray, offset: Int = 0, length: Int = content.size) {
        packet.writeFully(content, offset, length)
    }

    /**
     * Append raw bytes
     */
    fun bytes(content: ByteBuffer) {
        packet.writeFully(content)
    }

    /**
     * Append header line
     */
    fun headerLine(name: CharSequence, value: CharSequence) {
        packet.append(name)
        packet.append(": ")
        packet.append(value)
        packet.writeByte(CR)
        packet.writeByte(LF)
    }

    /**
     * Append an empty line (CR + LF in fact)
     */
    fun emptyLine() {
        packet.writeByte(CR)
        packet.writeByte(LF)
    }

    /**
     * Build a packet of request/response
     */
    fun build(): ByteReadPacket = packet.build()

    /**
     * Release all resources hold by the builder
     */
    fun release() {
        packet.release()
    }
}

private const val SP: Byte = 0x20
private const val CR: Byte = 0x0d
private const val LF: Byte = 0x0a
