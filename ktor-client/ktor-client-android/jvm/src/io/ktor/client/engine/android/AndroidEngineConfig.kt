/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.content.Context
import android.net.http.*
import io.ktor.client.engine.*
import io.ktor.utils.io.*
import java.net.*
import javax.net.ssl.*

/**
 * A configuration for the [Android] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig)
 */
public class AndroidEngineConfig : HttpClientEngineConfig() {
    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig.connectTimeout)
     */
    public var connectTimeout: Int = 100_000

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig.socketTimeout)
     */
    public var socketTimeout: Int = 100_000

    /**
     * Allows you to configure [HTTPS](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * Note that this configuration is not applied when [useHttpEngine] is active, as Android's
     * [HttpEngine] connections do not extend [HttpsURLConnection].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig.sslManager)
     */
    public var sslManager: (HttpsURLConnection) -> Unit = {}

    /**
     * Allows you to set engine-specific request configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig.requestConfig)
     */
    public var requestConfig: HttpURLConnection.() -> Unit = {}

    internal var httpEngineConfig: ((@KtorDsl HttpEngine.Builder).() -> Unit)? = null

    internal var httpEngineEnabled: Boolean = true

    internal var context: Context? = null

    /**
     * Enables Android's [HttpEngine] for executing requests on supported devices
     * (Android 14 / API 34+ or Android 12+ with SDK Extensions S version 7+).
     *
     * Use [configure] to customize the [HttpEngine.Builder], such as enabling Brotli, QUIC,
     * DNS options, or connection migration.
     *
     * If [HttpEngine] is not available on the device, or if a [proxy] is configured that is not
     * supported by [HttpEngine] (HTTP proxies require SDK Extensions S version 22+ / API 37+),
     * the engine falls back to the legacy [HttpURLConnection] implementation.
     *
     * Note that [sslManager] is not applied when [HttpEngine] is active because its connections
     * do not extend [HttpsURLConnection].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidEngineConfig.useHttpEngine)
     *
     * @param context Android [Context] used to initialize [HttpEngine.Builder]. `applicationContext` is used when available.
     * @param configure Optional configuration block applied to [HttpEngine.Builder].
     */
    public fun useHttpEngine(
        context: Context,
        configure: (@KtorDsl HttpEngine.Builder).() -> Unit = {}
    ) {
        this.context = context.applicationContext ?: context
        val previous = httpEngineConfig
        httpEngineConfig = if (previous == null) {
            configure
        } else {
            {
                previous()
                configure()
            }
        }
    }
}
