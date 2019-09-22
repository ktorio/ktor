/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
abstract class ClientUpgradeContent : OutgoingContent.NoContent() {
    private val content: ByteChannel by lazy { ByteChannel() }

    val output: ByteWriteChannel get() = content

    suspend fun pipeTo(output: ByteWriteChannel) {
        content.copyAndClose(output)
    }

    abstract fun verify(headers: Headers)
}
