/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * A message either from the client or the server,
 * that has [headers] associated.
 */
public interface HttpMessage {
    /**
     * Message [Headers]
     */
    public val headers: Headers
}

/**
 * A builder message either for the client or the server,
 * that has a [headers] builder associated.
 */
public interface HttpMessageBuilder {
    /**
     * MessageBuilder [HeadersBuilder]
     */
    public val headers: HeadersBuilder
}
