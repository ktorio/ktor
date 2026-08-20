/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.utils.io.*

@InternalAPI
public class CIOEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = CIO

    /**
     * CIO engine is often used in common sources, so it has a lower priority than stronger, platform-specific engines.
     */
    override val priority: Double get() = 0.0

    override fun toString(): String = "CIO"
}
