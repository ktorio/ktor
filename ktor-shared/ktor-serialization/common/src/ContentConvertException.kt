/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.websocket.*

public open class ContentConvertException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

public class JsonConvertException(
    message: String,
    cause: Throwable? = null
) : ContentConvertException(message, cause)

public open class WebsocketContentConvertException(
    message: String,
    cause: Throwable? = null
) : ContentConvertException(message, cause)

public class WebsocketConverterNotFoundException(
    message: String,
    cause: Throwable? = null
) : WebsocketContentConvertException(message, cause)

public class WebsocketDeserializeException(
    message: String,
    cause: Throwable? = null,
    public val frame: Frame
) : WebsocketContentConvertException(message, cause)
