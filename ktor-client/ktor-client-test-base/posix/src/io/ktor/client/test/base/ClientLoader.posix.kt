/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.test.base

import io.ktor.client.engine.*
import io.ktor.utils.io.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(InternalAPI::class)
internal actual val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>> get() = engines

@OptIn(ExperimentalNativeApi::class)
internal actual val platformName: String
    get() = "native/" + when (val family = Platform.osFamily) {
        OsFamily.MACOSX, OsFamily.IOS, OsFamily.TVOS, OsFamily.WATCHOS -> "darwin"
        else -> family.name.lowercase()
    }
