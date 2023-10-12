/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.defaultheaders

internal actual fun readVersion(): String {
    return Any::class.java.`package`.implementationVersion ?: "debug"
}
