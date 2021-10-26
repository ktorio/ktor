/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

public open class ContentConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)

public class JsonConvertException(message: String, cause: Throwable? = null) : ContentConvertException(message, cause)
