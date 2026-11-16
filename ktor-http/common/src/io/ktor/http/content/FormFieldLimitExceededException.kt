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
 * @property fieldName The field name extracted from `Content-Disposition` header, if available.
 */
public class FormFieldLimitExceededException private constructor(
    public val headers: Headers,
    public val fieldName: String?,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    public companion object {
        /**
         * Creates a [FormFieldLimitExceededException] from the part's [headers] and optional [cause].
         *
         * @param headers The headers of the part that exceeded the limit.
         * @param cause The underlying cause, if available.
         */
        public fun from(headers: Headers, cause: Throwable? = null): FormFieldLimitExceededException {
            val fieldName = headers[HttpHeaders.ContentDisposition]?.let {
                runCatching { ContentDisposition.parse(it) }.getOrNull()
            }?.name
            val message = if (fieldName != null) {
                "Form field limit exceeded for field '$fieldName'"
            } else {
                "Form field limit exceeded for an unknown field"
            }
            return FormFieldLimitExceededException(headers, fieldName, message, cause)
        }
    }
}
