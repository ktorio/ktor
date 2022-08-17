/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.workers.*

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 */
public actual val URLBuilder.Companion.origin: String
    get() = when {
        PlatformUtils.IS_BROWSER -> if (js("typeof window !== 'undefined'") as Boolean) {
            window.location.origin
        } else {
            js("self.location.origin") as String
        }
        else -> "http://localhost"
    }
