/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks

import io.ktor.client.engine.android.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.engine.okhttp.*

internal class ApacheClientBenchmarks : KtorClientBenchmarks(Apache)

internal class OkHttpClientBenchmarks : KtorClientBenchmarks(OkHttp)

internal class AndroidClientBenchmarks : KtorClientBenchmarks(Android)

internal class CIOClientBenchmarks : KtorClientBenchmarks(CIO)

internal class JettyClientBenchmarks : KtorClientBenchmarks(Jetty)
