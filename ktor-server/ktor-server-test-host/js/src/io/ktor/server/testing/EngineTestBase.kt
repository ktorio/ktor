/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.*
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

//not needed, as there is no CIO server on JS yet - just a stub for now
public actual abstract class EngineTestBase<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> actual constructor(
    applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : CoroutineScope {
    protected actual var port: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual var sslPort: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual var server: TEngine?
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual var callGroupSize: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual val exceptions: MutableList<Throwable>
        get() = TODO("Not yet implemented")
    protected actual var enableHttp2: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual var enableSsl: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    protected actual val testLog: Logger
        get() = TODO("Not yet implemented")
    actual override val coroutineContext: CoroutineContext
        get() = TODO("Not yet implemented")

    protected actual fun startServer(server: TEngine): List<Throwable> {
        TODO("Not yet implemented")
    }

    protected actual open fun createServer(
        log: Logger?,
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createServer(module: Application.() -> Unit): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createServer(
        log: Logger?,
        module: Application.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createServer(
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createAndStartServer(routingConfigurer: Routing.() -> Unit): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createAndStartServer(
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual fun createAndStartServer(
        log: Logger?,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        TODO("Not yet implemented")
    }

    protected actual open fun configure(configuration: TConfiguration) {
    }

    protected actual open fun features(
        application: Application,
        routingConfigurer: Routing.() -> Unit
    ) {
    }

    protected actual fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
    }

    protected actual fun withUrl(
        path: String,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
    }


}
