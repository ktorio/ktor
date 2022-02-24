/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

/**
 * Parsed `Sec-WebSocket-Accept` header item representation.
 *
 * @param name is extension name.
 * @param parameters is list of extension parameters.
 */
@ExperimentalWebSocketExtensionApi
public class WebSocketExtensionHeader(public val name: String, public val parameters: List<String>) {

    /**
     * Parse parameters keys and values
     */
    public fun parseParameters(): Sequence<Pair<String, String>> = parameters.asSequence().map {
        val equalsIndex = it.indexOf('=')
        if (equalsIndex < 0) return@map it to ""

        val key = it.substring(0 until equalsIndex)
        val value = if (equalsIndex + 1 < it.length) it.substring(equalsIndex + 1) else ""

        key to value
    }

    @Suppress("KDocMissingDocumentation")
    override fun toString(): String = "$name ${parametersToString()}"

    private fun parametersToString(): String =
        if (parameters.isEmpty()) "" else ", ${parameters.joinToString(",")}"
}

/**
 * Parse `Sec-WebSocket-Accept` header.
 */
@ExperimentalWebSocketExtensionApi
public fun parseWebSocketExtensions(value: String): List<WebSocketExtensionHeader> = value
    .split(";")
    .map { it ->
        val extension = it.split(",")
        val name = extension.first().trim()
        val parameters = extension.drop(1).map { it.trim() }
        WebSocketExtensionHeader(name, parameters)
    }
