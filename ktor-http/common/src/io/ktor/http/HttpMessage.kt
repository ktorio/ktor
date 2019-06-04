/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

/**
 * A message either from the client or the server,
 * that has [headers] associated.
 */
interface HttpMessage {
    /**
     * Message [Headers]
     */
    val headers: Headers
}

/**
 * A builder message either for the client or the server,
 * that has a [headers] builder associated.
 */
interface HttpMessageBuilder {
    /**
     * MessageBuilder [HeadersBuilder]
     */
    val headers: HeadersBuilder
}
