/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.sockets

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*

@OptIn(InternalAPI::class)
public fun SocketBuilder.quic(): QUICSocketBuilder = QUICSocketBuilder(selector, options.peer().quic())
