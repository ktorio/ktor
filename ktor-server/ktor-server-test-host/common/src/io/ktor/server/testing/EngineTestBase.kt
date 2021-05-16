/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public expect abstract class EngineTestBase<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : CoroutineScope {
    protected var port: Int
        private set
    protected var sslPort: Int
        private set
    protected var server: TEngine?
        private set
    protected var callGroupSize: Int
        private set
    protected val exceptions: MutableList<Throwable> //TODO
    protected var enableHttp2: Boolean
    protected var enableSsl: Boolean

    protected val testLog: Logger

    override val coroutineContext: CoroutineContext

    protected fun startServer(server: TEngine): List<Throwable>


    //TODO adding defaults fails on K/N
    protected open fun createServer(
        log: Logger?,
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine

    protected fun createServer(
        module: Application.() -> Unit
    ): TEngine

    protected fun createServer(
        log: Logger?,
        module: Application.() -> Unit
    ): TEngine

    protected fun createServer(
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine

    //TODO adding defaults fails on K/N
    protected fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine

    protected fun createAndStartServer(
        routingConfigurer: Routing.() -> Unit
    ): TEngine

    protected fun createAndStartServer(
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine

    protected fun createAndStartServer(
        log: Logger?,
        routingConfigurer: Routing.() -> Unit
    ): TEngine

    protected open fun configure(configuration: TConfiguration)

    protected open fun features(application: Application, routingConfigurer: Routing.() -> Unit)

    protected fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    )

    protected fun withUrl(
        path: String,
        block: suspend HttpResponse.(Int) -> Unit
    )
}
