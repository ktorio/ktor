/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.time.*

/**
 * Set 'Last-Modified` header value from [dateTime]
 */
fun HeadersBuilder.lastModified(dateTime: ZonedDateTime) = set(HttpHeaders.LastModified, dateTime.toHttpDateString())

/**
 * Set 'Expires` header value from [expires]
 */
fun HeadersBuilder.expires(expires: LocalDateTime) = set(HttpHeaders.Expires, expires.toHttpDateString())
