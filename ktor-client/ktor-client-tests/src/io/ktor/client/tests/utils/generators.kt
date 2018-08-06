package io.ktor.client.tests.utils

import kotlinx.io.core.*
import java.util.*

internal fun makeArray(size: Int): ByteArray = buildPacket { repeat(size) { writeByte(it.toByte()) } }.readBytes()

internal fun makeString(size: Int): String = buildString { repeat(size) { append(it.toChar()) } }.encodeBase64()

private fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())
