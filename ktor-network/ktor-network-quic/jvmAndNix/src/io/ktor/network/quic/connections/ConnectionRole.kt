/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

internal enum class ConnectionRole {
    SERVER, CLIENT;

    val peer by lazy {
        if (this == SERVER) {
            CLIENT
        } else {
            SERVER
        }
    }
}
