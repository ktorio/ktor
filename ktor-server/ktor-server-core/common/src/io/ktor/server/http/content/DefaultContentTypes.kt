/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.ContentType
import io.ktor.util.AttributeKey

/**
 * Used for inferring what the server might expect when serializing responses or parsing requests.
 */
public val DefaultContentTypesAttribute: AttributeKey<List<ContentType>> =
    AttributeKey<List<ContentType>>("DefaultContentTypes")
