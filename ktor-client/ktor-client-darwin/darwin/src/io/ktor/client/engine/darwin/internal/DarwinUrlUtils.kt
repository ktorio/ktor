/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.http.*
import platform.Foundation.*

internal fun Url.toNSUrl(): NSURL {
    val urlString = URLBuilder().also {
        it.protocol = protocol
        it.host = host.sanitize(NSCharacterSet.URLHostAllowedCharacterSet)
        it.port = port
        it.encodedPath = encodedPath.sanitize(NSCharacterSet.URLPathAllowedCharacterSet)
        it.encodedUser = encodedUser?.sanitize(NSCharacterSet.URLUserAllowedCharacterSet)
        it.encodedPassword = encodedPassword?.sanitize(NSCharacterSet.URLPasswordAllowedCharacterSet)

        val query = encodedQuery.sanitize(NSCharacterSet.URLQueryAllowedCharacterSet)

        it.encodedParameters = ParametersBuilder().apply { appendAll(parseQueryString(query, decode = false)) }
        it.encodedFragment = encodedFragment.sanitize(NSCharacterSet.URLFragmentAllowedCharacterSet)
        it.trailingQuery = trailingQuery
    }.buildString()

    return NSURL(string = urlString)
}

private fun String.asNSString(): NSString = this as NSString

private fun String.sanitize(allowed: NSCharacterSet): String =
    asNSString().stringByAddingPercentEncodingWithAllowedCharacters(allowed)!!
