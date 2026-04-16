/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.util.*

/**
 * Used for inferring what the server might expect when serializing responses or parsing requests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.DefaultContentTypesAttribute)
 */
public val DefaultContentTypesAttribute: AttributeKey<List<ContentType>> =
    AttributeKey<List<ContentType>>("DefaultContentTypes")
