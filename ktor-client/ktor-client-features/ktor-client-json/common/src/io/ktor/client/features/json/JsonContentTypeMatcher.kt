/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.json

import io.ktor.http.*

internal class JsonContentTypeMatcher : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (ContentType.Application.Json.match(contentType)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value.startsWith("application/") && value.endsWith("+json")
    }
}
