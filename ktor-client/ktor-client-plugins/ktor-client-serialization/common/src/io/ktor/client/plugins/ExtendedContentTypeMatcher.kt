/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.http.*

/**
 * Matcher that accepts all extended [contentType] types.
 * e.g: [ContentType.Application.Json] would match blob `application/(*)+json` as well as base type of `application/json`
 */
public class ExtendedContentTypeMatcher(private val contentType: ContentType) : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (contentType.match(contentType)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value.startsWith("${contentType.contentType}/") && value.endsWith("+${contentType.contentSubtype}")
    }
}
