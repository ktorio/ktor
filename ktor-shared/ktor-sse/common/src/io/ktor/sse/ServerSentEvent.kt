/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sse

/**
 *  Server-sent event.
 *
 *  @property data - a data field of the event.
 *  @property event - a string identifying the type of event.
 *  @property id - an event ID.
 *  @property retry - a reconnection time, in milliseconds to wait before reconnecting.
 *  @property comments - a comment lines starting with a ':' character.
 */
public class ServerSentEvent(
    public val data: String? = null,
    public val event: String? = null,
    public val id: String? = null,
    public val retry: Long? = null,
    public val comments: String? = null
) {
    override fun toString(): String {
        return StringBuilder().apply {
            appendField("data", data)
            appendField("event", event)
            appendField("id", id)
            appendField("retry", retry)
            appendField("comments", comments)
        }.toString()
    }
}

@Suppress("KDocMissingDocumentation")
public class SSEException : IllegalStateException {
    public constructor(cause: Throwable?) : super(cause)
    public constructor(message: String) : super(message)
}

private fun <T> StringBuilder.appendField(name: String, value: T?) {
    if (value != null) {
        append("$name: $value\n")
    }
}
