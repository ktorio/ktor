/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import java.time.*

/**
 * Wait for websocket session completion
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun TestApplicationResponse.awaitWebSocket(duration: Duration) {
    awaitWebSocket(duration.toMillis())
}
