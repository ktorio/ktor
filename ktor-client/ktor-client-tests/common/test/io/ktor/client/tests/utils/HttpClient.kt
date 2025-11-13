/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.http.*

internal val HttpClient.engineName get() = engine::class.simpleName?.removeSuffix("ClientEngine")

private val allMethods = HttpMethod.DefaultMethods + HttpMethod.Trace

internal fun HttpClient.supportedMethods(): List<HttpMethod> = when (engineName) {
    // PATCH is not supported by HttpURLConnection
    // https://bugs.openjdk.org/browse/JDK-7016595
    "Android" -> allMethods - HttpMethod.Patch
    // Js engine throws: TypeError: 'TRACE' HTTP method is unsupported.
    "Js" -> allMethods - HttpMethod.Trace
    else -> allMethods
}

private val HttpMethod.Companion.Trace get() = HttpMethod("TRACE")
