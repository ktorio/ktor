/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sse

/**
 *  Server-sent event.
 *
 *  @property event - a string identifying the type of event described
 *  @property id - the event ID.
 *  @property data - the data field for the message.
 */
public class ServerSentEvent(public val event: String? = null, public val id: String? = null, public val data: String) {
    override fun toString(): String {
        return "ServerSentEvent(event=$event, id=$id, data='$data')"
    }
}

@Suppress("KDocMissingDocumentation")
public class SSEException : IllegalStateException {
    public constructor(cause: Throwable?) : super(cause)
    public constructor(message: String) : super(message)
}
