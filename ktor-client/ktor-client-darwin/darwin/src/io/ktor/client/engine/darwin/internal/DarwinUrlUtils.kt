/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.http.*
import io.ktor.util.*
import platform.Foundation.*

internal fun Url.toNSUrl(config: UrlAllowedCharactersConfig = UrlAllowedCharactersConfig()): NSURL {
    val userEncoded = encodedUser.orEmpty().isEncoded(config.userAllowedCharacterSet)
    val passwordEncoded = encodedPassword.orEmpty().isEncoded(config.userAllowedCharacterSet)
    val hostEncoded = host.isEncoded(config.hostAllowedCharacterSet)
    val pathEncoded = encodedPath.isEncoded(config.pathAllowedCharacterSet)
    val queryEncoded = encodedQuery.isEncoded(config.queryAllowedCharacterSet)
    val fragmentEncoded = encodedFragment.isEncoded(config.fragmentAllowedCharacterSet)
    if (userEncoded && passwordEncoded && hostEncoded && pathEncoded && queryEncoded && fragmentEncoded) {
        return NSURL(string = toString())
    }

    with(Url(this.toString())) {
        val components = NSURLComponents()

        components.scheme = protocol.name

        components.percentEncodedUser = when {
            userEncoded -> encodedUser
            else -> user?.sanitize(config.userAllowedCharacterSet)
        }
        components.percentEncodedPassword = when {
            passwordEncoded -> encodedPassword
            else -> password?.sanitize(config.userAllowedCharacterSet)
        }

        components.percentEncodedHost = when {
            hostEncoded -> host
            else -> host.sanitize(config.hostAllowedCharacterSet)
        }
        if (port != DEFAULT_PORT && port != protocol.defaultPort) {
            components.port = NSNumber(int = port)
        }

        components.percentEncodedPath = when {
            pathEncoded -> encodedPath
            else -> rawSegments.joinToString("/").sanitize(config.pathAllowedCharacterSet)
        }

        when {
            encodedQuery.isEmpty() -> components.percentEncodedQuery = null
            queryEncoded -> components.percentEncodedQuery = encodedQuery
            else -> components.percentEncodedQueryItems = parameters.toMap()
                .flatMap { (key, value) -> if (value.isEmpty()) listOf(key to null) else value.map { key to it } }
                .map { NSURLQueryItem(it.first.encodeQueryKey(config), it.second?.encodeQueryValue(config)) }
        }

        components.percentEncodedFragment = when {
            encodedFragment.isEmpty() -> null
            fragmentEncoded -> encodedFragment
            else -> fragment.sanitize(config.fragmentAllowedCharacterSet)
        }

        return components.URL ?: error("Invalid url: $this")
    }
}

private fun String.sanitize(allowed: NSCharacterSet): String =
    asNSString().stringByAddingPercentEncodingWithAllowedCharacters(allowed)!!

private fun String.encodeQueryKey(config: UrlAllowedCharactersConfig): String =
    encodeQueryValue(config).replace("=", "%3D")

private fun String.encodeQueryValue(config: UrlAllowedCharactersConfig): String =
    asNSString().stringByAddingPercentEncodingWithAllowedCharacters(config.queryAllowedCharacterSet)!!
        .replace("&", "%26")
        .replace(";", "%3B")

private fun String.isEncoded(allowed: NSCharacterSet) =
    all { it == '%' || allowed.characterIsMember(it.code.toUShort()) }

@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.asNSString(): NSString = this as NSString
