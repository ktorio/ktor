/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.request

import io.ktor.util.*
import io.ktor.util.reflect.*

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationReceiveRequest", "io.ktor.server.request.*")
)
public class ApplicationReceiveRequest(
    public val typeInfo: TypeInfo,
    public val value: Any,
    public val reusableValue: Boolean = false
)

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationReceivePipeline", "io.ktor.server.request.*")
)
public open class ApplicationReceivePipeline(
    public val developmentMode: Boolean = false
)

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ContentTransformationException", "io.ktor.server.request.*")
)
public typealias ContentTransformationException = Nothing

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DoubleReceivePreventionToken", "io.ktor.server.request.*")
)
private object DoubleReceivePreventionToken

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DoubleReceivePreventionTokenKey", "io.ktor.server.request.*")
)
private val DoubleReceivePreventionTokenKey = AttributeKey<DoubleReceivePreventionToken>("DoubleReceivePreventionToken")

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RequestAlreadyConsumedException", "io.ktor.server.request.*")
)
public class RequestAlreadyConsumedException : IllegalStateException(
    "Request body has already been consumed (received)."
)
