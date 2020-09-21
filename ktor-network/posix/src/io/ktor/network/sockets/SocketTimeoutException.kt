/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.errors.*

@Suppress("EXPECT_WITHOUT_ACTUAL")
public actual class SocketTimeoutException actual constructor(message: String) : IOException(message)
