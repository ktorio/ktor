/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.slf4j.*
import org.slf4j.helpers.*
import kotlin.coroutines.*

fun fakeApplication(): Application {
    return Application(FakeEnvironment())
}

class FakeEnvironment(
    override val parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    override val classLoader: ClassLoader = object {}.javaClass.classLoader,
    override val log: Logger = NOPLogger.NOP_LOGGER,
    override val config: ApplicationConfig = MapApplicationConfig(),
    override val monitor: Events = Events(),
    override val rootPath: String = "/",
    override val developmentMode: Boolean = true
) : ApplicationEnvironment


