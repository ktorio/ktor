/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Represents a simple status code response with no content
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.HttpStatusCodeContent)
 *
 * @param value - status code to be sent
 */
public class HttpStatusCodeContent(private val value: HttpStatusCode) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode
        get() = value

    override fun toString(): String = "HttpStatusCodeContent($value)"
}
