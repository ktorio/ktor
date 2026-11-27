/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.forwardedheaders

internal fun String.isNotHostAddress(): Boolean {
    return if (contains(':')) {
        return true
    } else {
        none { it.isLetter() }
    }
}

/**
 * Removes the port from a [Forwarded](https://datatracker.ietf.org/doc/html/rfc7239#section-4)
 * endpoint (`nodename [ ":" port ]`) so that `remoteHost` and `remoteAddress`
 * hold the node name only.
 *
 * A bracketed IPv6 literal is unwrapped; a bare IPv6 literal (several colons) is kept as is.
 */
internal fun String.dropForwardedPort(): String {
    if (startsWith('[')) {
        val closingBracketIndex = indexOf(']')
        if (closingBracketIndex != -1) return substring(1, closingBracketIndex)
    }

    val portSeparatorIndex = indexOf(':')
    if (portSeparatorIndex == -1 || portSeparatorIndex != lastIndexOf(':')) return this

    return substringAfter(':').toIntOrNull()?.let { substringBefore(':') } ?: this
}
