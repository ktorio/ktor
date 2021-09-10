/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.errors

import io.ktor.utils.io.streams.*
import kotlinx.cinterop.*
import platform.posix.*

public actual fun strerror_r(errnum: Int, msg: CArrayPointer<ByteVar>, size: _size_t): Int =
    platform.posix.strerror_r(errnum, msg, size.convert())
