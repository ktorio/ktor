/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.utils.io.*
import kotlinx.io.IOException

@InternalAPI
public class UnsupportedMediaTypeExceptionCIO(message: String) : IOException(message)
