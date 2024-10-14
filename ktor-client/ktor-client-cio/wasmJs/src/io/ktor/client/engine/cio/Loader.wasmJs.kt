/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.util.*
import io.ktor.utils.io.*

@Suppress("DEPRECATION")
@OptIn(InternalAPI::class, ExperimentalStdlibApi::class)
@EagerInitialization
private val initHook: Unit = run {
    if (PlatformUtils.IS_NODE) engines.append(CIO)
}
