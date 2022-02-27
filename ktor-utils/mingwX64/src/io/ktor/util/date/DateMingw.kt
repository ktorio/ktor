/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("FunctionName")
internal actual fun system_time(tm: CValuesRef<tm>?): Long {
    return _mkgmtime(tm).convert()
}
