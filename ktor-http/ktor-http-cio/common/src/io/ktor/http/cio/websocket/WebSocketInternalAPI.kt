/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

/**
 * API marked with this annotation is internal and not intended to be used outside of ktor
 * It is not recommended to use it as it may be changed in the future without notice or
 * it may be too low-level so could damage your data.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This ktor WebSocket API is internal and should be never used outside. " +
        "It is not guaranteed to work the same in future releases and may be changed or removed."
)
@Experimental(level = Experimental.Level.ERROR)
public annotation class WebSocketInternalAPI
