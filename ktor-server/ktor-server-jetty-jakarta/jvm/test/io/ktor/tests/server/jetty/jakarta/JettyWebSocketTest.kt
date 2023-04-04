/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.testing.suites.*

class JettyWebSocketTest : WebSocketEngineSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
