/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.apache

import java.net.*

/**
 * Checks the message of the exception and identifies timeout exception by it.
 */
internal fun ConnectException.isTimeoutException() = message?.contains("Timeout connecting") ?: false
