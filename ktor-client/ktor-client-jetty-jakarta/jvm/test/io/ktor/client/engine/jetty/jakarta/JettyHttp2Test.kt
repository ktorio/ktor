/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.tests.*
import kotlin.test.Ignore

@Ignore // KTOR-9094 Jetty Client: Support HTTP/2 clear-text traffic (h2c)
class JettyHttp2Test : Http2Test<JettyEngineConfig>(Jetty)
