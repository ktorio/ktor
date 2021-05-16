/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

public actual abstract class HttpServerTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> actual constructor(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : CommonHttpServerTestSuite<TEngine, TConfiguration>(hostFactory)
