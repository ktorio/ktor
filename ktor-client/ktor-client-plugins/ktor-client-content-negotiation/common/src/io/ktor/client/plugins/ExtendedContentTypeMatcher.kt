/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.http.*

/**
 * Matcher that accepts all extended [ContentType]s
 *
 * e.g. Given [baseContentType] as `x/y`, this matcher would match all `x/y` and `x/(*)+y`
 */
public class ExtendedContentTypeMatcher(private val baseContentType: ContentType) : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (contentType.match(baseContentType)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value.startsWith("${baseContentType.contentType}/") && value.endsWith("+${baseContentType.contentSubtype}")
    }
}
