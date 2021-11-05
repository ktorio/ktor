/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import kotlinx.cinterop.*
import libcurl.*

internal actual fun curlInitBridge(): Int = curl_global_init(CURL_GLOBAL_ALL.convert()).convert()
