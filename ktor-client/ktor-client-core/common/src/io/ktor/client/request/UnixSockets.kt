/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.request

import io.ktor.client.engine.*
import io.ktor.utils.io.InternalAPI

@InternalAPI
public class UnixSocketSettings(public val path: String)

@OptIn(InternalAPI::class)
public data object UnixSocketCapability : HttpClientEngineCapability<UnixSocketSettings>
