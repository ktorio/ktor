/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.features

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.reflect.*

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("BadRequestException", "io.ktor.server.plugins.*")
)
public open class BadRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("NotFoundException", "io.ktor.server.plugins.*")
)
public class NotFoundException(message: String? = "Resource not found") : Exception(message)

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("MissingRequestParameterException", "io.ktor.server.plugins.*")
)
@OptIn(ExperimentalCoroutinesApi::class)
public class MissingRequestParameterException(
    public val parameterName: String
) : BadRequestException("Request parameter $parameterName is missing")

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ParameterConversionException", "io.ktor.server.plugins.*")
)
@OptIn(ExperimentalCoroutinesApi::class)
public class ParameterConversionException(
    public val parameterName: String,
    public val type: String,
    cause: Throwable? = null
) : BadRequestException("Request parameter $parameterName couldn't be parsed/converted to $type", cause)

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ContentTransformationException", "io.ktor.server.plugins.*")
)
public abstract class ContentTransformationException(message: String) : Exception(message)

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CannotTransformContentToTypeException", "io.ktor.server.plugins.*")
)
@OptIn(ExperimentalCoroutinesApi::class)
public class CannotTransformContentToTypeException(
    private val type: KType
) : ContentTransformationException("Cannot transform this request's content to $type")

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("UnsupportedMediaTypeException", "io.ktor.server.plugins.*")
)
@OptIn(ExperimentalCoroutinesApi::class)
public class UnsupportedMediaTypeException(
    private val contentType: ContentType
) : ContentTransformationException("Content type $contentType is not supported")
