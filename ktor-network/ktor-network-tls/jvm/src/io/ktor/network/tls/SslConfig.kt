/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.util.*
import java.security.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.time.*

@InternalAPI
public fun Socket.ssl(
    coroutineContext: CoroutineContext,
    config: SslConfig
): Socket = TODO()

public fun SslConfig(block: SslConfigBuilder.() -> Unit): SslConfig = TODO()

public sealed interface SslConfig {
    //TODO: what should be here
}

public sealed interface SslConfigBuilder {

    public fun contextProvider(provider: Provider?)
    public fun keyManager(factory: KeyManagerFactory?)
    public fun trustManager(factory: TrustManagerFactory?)
    public fun secureRandom(random: SecureRandom?)

    public fun session(cacheSize: Int, timeout: Duration) //timeout should be in seconds

    //delicate api
    public fun parameters(block: SSLParameters.() -> Unit)
}

public sealed interface SslClientConfigBuilder : SslConfigBuilder {
    public fun reuseSession(value: Boolean)
}

public sealed interface SslServerConfigBuilder : SslConfigBuilder {
    public fun clientAuthType(value: SslClientAuthType)
}

public enum class SslClientAuthType {
    NONE,           // turn off client authentication
    OPTIONAL,       // need to request client authentication
    REQUIRED        // require client authentication
}
