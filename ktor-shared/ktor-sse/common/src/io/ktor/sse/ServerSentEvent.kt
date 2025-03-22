/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sse

import io.ktor.utils.io.*

/**
 *  Server-sent event interface.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.sse.ServerSentEventMetadata)
 *
 *  @property data data field of the event.
 *  @property event string identifying the type of event.
 *  @property id event ID.
 *  @property retry reconnection time, in milliseconds to wait before reconnecting.
 *  @property comments comment lines starting with a ':' character.
 *
 *  @see ServerSentEvent with default String parameter `data`
 *  @see TypedServerSentEvent with parameterized parameter `data`
 */
public sealed interface ServerSentEventMetadata<T> {
    public val data: T?
    public val event: String?
    public val id: String?
    public val retry: Long?
    public val comments: String?
}

/**
 *  Server-sent event.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.sse.ServerSentEvent)
 *
 *  @property data data field of the event.
 *  @property event string identifying the type of event.
 *  @property id event ID.
 *  @property retry reconnection time, in milliseconds to wait before reconnecting.
 *  @property comments comment lines starting with a ':' character.
 *
 *  @see TypedServerSentEvent with parameterized parameter `data`
 */
public data class ServerSentEvent(
    override val data: String? = null,
    override val event: String? = null,
    override val id: String? = null,
    override val retry: Long? = null,
    override val comments: String? = null
) : ServerSentEventMetadata<String> {
    override fun toString(): String = eventToString(data, event, id, retry, comments)
}

/**
 *  Server-sent event with generic parameter [data].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.sse.TypedServerSentEvent)
 *
 *  @property data data field of the event.
 *  @property event string identifying the type of event.
 *  @property id event ID.
 *  @property retry reconnection time, in milliseconds to wait before reconnecting.
 *  @property comments comment lines starting with a ':' character.
 *
 *  @see ServerSentEvent with default String parameter `data`
 */
public data class TypedServerSentEvent<T>(
    override val data: T? = null,
    override val event: String? = null,
    override val id: String? = null,
    override val retry: Long? = null,
    override val comments: String? = null
) : ServerSentEventMetadata<T> {
    @InternalAPI
    public fun toString(serializer: (T) -> String): String =
        eventToString(data?.let { serializer(it) }, event, id, retry, comments)
}

private fun eventToString(data: String?, event: String?, id: String?, retry: Long?, comments: String?): String {
    return buildString {
        appendField("data", data)
        appendField("event", event)
        appendField("id", id)
        appendField("retry", retry)
        appendField("", comments)
    }
}

@OptIn(InternalAPI::class)
private fun <T> StringBuilder.appendField(name: String, value: T?) {
    if (value != null) {
        val values = value.toString().split(END_OF_LINE_VARIANTS)
        values.forEach {
            append("$name$COLON$SPACE$it$END_OF_LINE")
        }
    }
}

@InternalAPI
public const val COLON: String = ":"

@InternalAPI
public const val SPACE: String = " "

@InternalAPI
public const val END_OF_LINE: String = "\r\n"

@InternalAPI
public val END_OF_LINE_VARIANTS: Regex = Regex("\r\n|\r|\n")
