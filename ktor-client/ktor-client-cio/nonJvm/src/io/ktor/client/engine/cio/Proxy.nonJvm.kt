/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.ProxyConfig
import io.ktor.http.Url

internal actual fun lookupGlobalProxy(url: Url): ProxyConfig? = null
