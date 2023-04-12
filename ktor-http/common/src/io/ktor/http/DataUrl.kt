/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlin.text.toByteArray

public class DataUrl(
    internal val originalUrl: String,
    internal val dataIndex: Int,
    public val contentType: ContentType,
    public val contentTypeDefined: Boolean,
    public val inBase64: Boolean,
) {
    public val data: ByteArray by lazy {
        if (inBase64) {
            buildPacket {
                writeText(
                    originalUrl.subSequence(dataIndex until originalUrl.length).dropLastWhile { it == '=' }
                )
            }.decodeBase64Bytes().readBytes()
        } else {
            val charset = if (contentType.charset() != null) {
                contentType.charset()!!
            } else {
                Charsets.UTF_8
            }

            originalUrl.decodeURLPart(dataIndex).toByteArray(charset)
        }
    }

    internal companion object {
        internal fun parse(str: String, startIndex: Int): DataUrl {
            var i = startIndex
            val mimeType = StringBuilder(16)
            val maybeBase64 = StringBuilder(7)
            var base64Start = -1
            var isBase64 = false

            // Parse mime type and base64
            while (i < str.length && str[i] != ',') {
                if (str[i] == ';') {
                    base64Start = i
                    while (i < str.length && str[i] != '=' && str[i] != ',') {
                        maybeBase64.append(str[i++])
                    }

                    if (i >= str.length) {
                        throw IllegalArgumentException("Expect , at position $i")
                    }
                    isBase64 = str[i] != '='
                    if (isBase64) {
                        break
                    } else {
                        mimeType.append(maybeBase64)
                        maybeBase64.clear()
                    }
                }

                mimeType.append(str[i++])
            }

            if (isBase64 && maybeBase64.toString() != ";base64") {
                throw IllegalArgumentException("Expect ';base64' string at position $base64Start")
            }

            if (i >= str.length) {
                throw IllegalArgumentException("Expect , or ; at position $i")
            }

            if (str[i] == ',') {
                i += 1 // Skip comma
            }

            val contentType = if (mimeType.isEmpty()) {
                ContentType.Text.Plain.withCharset(Charsets.US_ASCII)
            } else {
                ContentType.parse(mimeType.toString())
            }

            return DataUrl(
                originalUrl = str,
                dataIndex = i,
                contentType = contentType,
                contentTypeDefined = mimeType.isNotEmpty(),
                inBase64 = isBase64
            )
        }
    }

}
