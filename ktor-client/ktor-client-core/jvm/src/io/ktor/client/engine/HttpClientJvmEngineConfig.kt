/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.util.*

abstract class HttpClientJvmEngineConfig() : HttpClientEngineConfig() {
    /**
     * Network threads count advice.
     */
    @KtorExperimentalAPI
    var threadsCount: Int = 4

    /**
     * Use daemon thread pool
     */
    @KtorExperimentalAPI
    var daemon: Boolean = false
}
