/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.http.*
import kotlinx.io.IOException

/**
 * Exception thrown when a multipart form field exceeds the specified limit.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.FormFieldLimitExceededException)
 *
 * @property headers The headers of the part that exceeded the limit.
 */
public class FormFieldLimitExceededException(
    public val headers: Headers,
    message: String = defaultFormFieldLimitMessage(headers),
    cause: Throwable? = null
) : IOException(message, cause) {

    private companion object {
        private fun defaultFormFieldLimitMessage(headers: Headers): String {
            val name = headers[HttpHeaders.ContentDisposition]?.let {
                runCatching { ContentDisposition.parse(it) }.getOrNull()
            }?.name
            return if (name != null) {
                "Form field limit exceeded for field '$name'"
            } else {
                "Form field limit exceeded for part with headers: $headers"
            }
        }
    }

    /**
     * The field name extracted from `Content-Disposition` header, if available.
     */
    public val fieldName: String? get() = headers[HttpHeaders.ContentDisposition]?.let {
        runCatching { ContentDisposition.parse(it) }.getOrNull()
    }?.name
}
