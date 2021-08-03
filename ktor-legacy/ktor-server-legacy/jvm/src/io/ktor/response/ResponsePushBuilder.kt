/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.response

import io.ktor.http.*
import io.ktor.http.content.*

@Deprecated(
    message = "Moved to io.ktor.server.response",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponsePushBuilder", "io.ktor.server.response.*")
)
@UseHttp2Push
public interface ResponsePushBuilder {
    public val url: URLBuilder
    public val headers: HeadersBuilder
    public var method: HttpMethod
    public val versions: MutableList<Version>
}
