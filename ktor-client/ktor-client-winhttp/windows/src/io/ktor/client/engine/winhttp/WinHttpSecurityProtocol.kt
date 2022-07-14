/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

public enum class WinHttpSecurityProtocol(internal val value: Int) {
    Default(0),
    Tls10(0x00000080),
    Tls11(0x00000200),
    Tls12(0x00000800),
    Tls13(0x00002000),
}
