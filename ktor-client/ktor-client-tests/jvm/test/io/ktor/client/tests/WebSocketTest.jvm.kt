/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

// JDK 14 and older discard the body of a rejected WebSocket handshake.
internal actual val PLATFORM_ENGINES_WITHOUT_HANDSHAKE_RESPONSE_BODY: List<String> =
    if (javaFeatureVersion() < 15) listOf("Java") else emptyList()

// `java.specification.version` is "1.8" on JDK 8 and "11" on JDK 11.
private fun javaFeatureVersion(): Int =
    System.getProperty("java.specification.version").removePrefix("1.").toInt()
