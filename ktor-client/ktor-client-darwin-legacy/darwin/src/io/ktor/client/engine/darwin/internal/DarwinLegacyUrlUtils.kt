/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.http.*
import io.ktor.util.*
import platform.Foundation.*

internal fun Url.toNSUrl(): NSURL {
    val userEncoded = encodedUser.orEmpty().isEncoded(NSCharacterSet.URLUserAllowedCharacterSet)
    val passwordEncoded = encodedPassword.orEmpty().isEncoded(NSCharacterSet.URLUserAllowedCharacterSet)
    val hostEncoded = host.isEncoded(NSCharacterSet.URLHostAllowedCharacterSet)
    val pathEncoded = encodedPath.isEncoded(NSCharacterSet.URLPathAllowedCharacterSet)
    val queryEncoded = encodedQuery.isEncoded(NSCharacterSet.URLQueryAllowedCharacterSet)
    val fragmentEncoded = encodedFragment.isEncoded(NSCharacterSet.URLFragmentAllowedCharacterSet)
    if (userEncoded && passwordEncoded && hostEncoded && pathEncoded && queryEncoded && fragmentEncoded) {
        return NSURL(string = toString())
    }

    val components = NSURLComponents()

    components.scheme = protocol.name

    components.percentEncodedUser = when {
        userEncoded -> encodedUser
        else -> user?.sanitize(NSCharacterSet.URLUserAllowedCharacterSet)
    }
    components.percentEncodedPassword = when {
        passwordEncoded -> encodedPassword
        else -> password?.sanitize(NSCharacterSet.URLUserAllowedCharacterSet)
    }

    components.percentEncodedHost = when {
        hostEncoded -> host
        else -> host.sanitize(NSCharacterSet.URLHostAllowedCharacterSet)
    }
    if (port != DEFAULT_PORT && port != protocol.defaultPort) {
        components.port = NSNumber(int = port)
    }

    components.percentEncodedPath = when {
        pathEncoded -> encodedPath
        else -> pathSegments.joinToString("/").sanitize(NSCharacterSet.URLPathAllowedCharacterSet)
    }

    when {
        encodedQuery.isEmpty() -> components.percentEncodedQuery = null
        queryEncoded -> components.percentEncodedQuery = encodedQuery
        else -> components.percentEncodedQueryItems = parameters.toMap()
            .flatMap { (key, value) -> if (value.isEmpty()) listOf(key to null) else value.map { key to it } }
            .map { NSURLQueryItem(it.first.encodeQueryKey(), it.second?.encodeQueryValue()) }
    }

    components.percentEncodedFragment = when {
        encodedFragment.isEmpty() -> null
        fragmentEncoded -> encodedFragment
        else -> fragment.sanitize(NSCharacterSet.URLFragmentAllowedCharacterSet)
    }

    return components.URL ?: error("Invalid url: $this")
}

private fun String.sanitize(allowed: NSCharacterSet): String =
    asNSString().stringByAddingPercentEncodingWithAllowedCharacters(allowed)!!

private fun String.encodeQueryKey(): String =
    encodeQueryValue().replace("=", "%3D")

private fun String.encodeQueryValue(): String =
    asNSString().stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet)!!
        .replace("&", "%26")
        .replace(";", "%3B")

private fun String.isEncoded(allowed: NSCharacterSet) =
    all { it == '%' || allowed.characterIsMember(it.code.toUShort()) }

@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.asNSString(): NSString = this as NSString
