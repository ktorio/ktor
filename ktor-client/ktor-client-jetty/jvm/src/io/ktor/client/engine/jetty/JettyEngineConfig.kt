/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import org.eclipse.jetty.util.ssl.*


/**
 * Configuration for [Jetty] implementation of [HttpClientEngineFactory].
 */
class JettyEngineConfig : HttpClientJvmEngineConfig() {
    /**
     * A Jetty's [SslContextFactory]. By default it trusts all the certificates.
     */
    var sslContextFactory: SslContextFactory = SslContextFactory()
}
