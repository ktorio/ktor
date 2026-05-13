/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import libcurl.CURL_READFUNC_ABORT
import libcurl.CURL_READFUNC_PAUSE
import libcurl.CURL_WRITEFUNC_ERROR
import libcurl.CURL_WRITEFUNC_PAUSE
import platform.posix.size_t

@OptIn(ExperimentalForeignApi::class)
internal object Libcurl {
    val WRITEFUNC_PAUSE: size_t = CURL_WRITEFUNC_PAUSE.convert()
    val WRITEFUNC_ERROR: size_t = CURL_WRITEFUNC_ERROR.convert()

    val READFUNC_PAUSE: size_t = CURL_READFUNC_PAUSE.convert()
    val READFUNC_ABORT: size_t = CURL_READFUNC_ABORT.convert()
}
