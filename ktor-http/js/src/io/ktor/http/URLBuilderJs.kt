/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import kotlinx.browser.*

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 */
internal actual val URLBuilder.Companion.originHost: String?
    get() = if (PlatformUtils.IS_BROWSER) {
        window.location.origin
    } else null
