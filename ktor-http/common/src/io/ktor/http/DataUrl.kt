/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*

public class DataUrl(
    internal val originalData: String,

    public val contentType: ContentType,
    public val contentTypeDefined: Boolean,
    public val inBase64: Boolean,
) {
    public val data: ByteArray by lazy {
        if (inBase64) {
            originalData.decodeBase64Bytes()
        } else {
            val charset = if (!contentTypeDefined) {
                Charsets.US_ASCII
            } else if (contentType.charset() != null) {
                contentType.charset()!!
            } else {
                Charsets.UTF_8
            }

            originalData.decodeURLPart(0).toByteArray(charset)
        }
    }
}
