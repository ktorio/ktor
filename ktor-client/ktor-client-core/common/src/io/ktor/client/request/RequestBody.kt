/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.utils.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.*

internal val BodyTypeAttributeKey: AttributeKey<KType> = AttributeKey("BodyTypeAttributeKey")

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T : Any> tryGetType(ignored: T): KType? = try {
    // We need to wrap getting type in try catch because of KT-42913
    typeOf<T>()
} catch (_: Throwable) {
    null
}

public inline fun <reified T> HttpRequestBuilder.body(body: T) {
    when (body) {
        null -> {
            this.body = EmptyContent
        }
        is String,
        is OutgoingContent,
        is ByteArray,
        is ByteReadChannel -> {
            this.body = body
        }
        else -> {
            this.body = body
            bodyType = tryGetType(body)
        }
    }
}
