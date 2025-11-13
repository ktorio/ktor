/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.tests.*

class JettyHttp2Test : Http2Test<JettyEngineConfig>(Jetty)
