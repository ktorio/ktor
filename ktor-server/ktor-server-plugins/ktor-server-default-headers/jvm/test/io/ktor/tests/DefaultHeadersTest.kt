/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class DefaultHeadersTest {
    private val app = Application(
        object : ApplicationEnvironment {
            override val parentCoroutineContext get() = EmptyCoroutineContext
            override val classLoader get() = TODO()
            override val log get() = TODO()
            override val config get() = TODO()
            override val monitor get() = TODO()
            override val rootPath get() = TODO()
            override val developmentMode get() = true
        }
    )

    private val call = TestApplicationCall(app, coroutineContext = EmptyCoroutineContext)

    @Test
    fun addsServerHeaderWithFallbackPackageNameAndVersion() {
        DefaultHeaders.Plugin.install(app) {}
        executePipeline()
        assertEquals("Ktor/debug", call.response.headers["Server"])
    }

    @Test
    fun serverHeaderIsNotModifiedIfPresent() {
        DefaultHeaders.Plugin.install(app) {
            header(HttpHeaders.Server, "xserver/1.0")
        }
        executePipeline()
        assertEquals("xserver/1.0", call.response.headers["Server"])
    }

    private fun executePipeline() {
        runBlocking {
            app.execute(call, Unit)
        }
    }
}
