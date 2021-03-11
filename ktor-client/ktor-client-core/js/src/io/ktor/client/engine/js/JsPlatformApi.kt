/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.js.compatibility.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.w3c.dom.*
import org.w3c.fetch.*
import kotlin.js.*

/**
 * Platform specific API for JS
 */
public interface JsPlatformApi {

    public fun fetch(url: String): Promise<Response>

    public fun fetch(url: String, init: RequestInit): Promise<Response>

    public fun createAbortController(): AbortController

    public fun createHeaders(): Headers

    public fun createWebSocket(urlString: String): WebSocket

    public fun readBody(scope: CoroutineScope, response: Response): ByteReadChannel
}
