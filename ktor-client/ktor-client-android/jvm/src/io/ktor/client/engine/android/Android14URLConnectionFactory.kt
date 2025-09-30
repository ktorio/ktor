/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.net.http.HttpEngine
import java.net.HttpURLConnection
import java.net.URI

// @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
internal class AndroidNetHttpEngineFactory(private val config: AndroidEngineConfig) : URLConnectionFactory {
    private val engine by lazy { buildEngine() }

    /**
     * Builds an HttpEngine configured with the Android application context from the provided config.
     *
     * Retrieves the configuration's context (must be non-null) and uses its applicationContext to construct
     * an HttpEngine with the settings from `config.httpEngineConfig`.
     *
     * @return A configured `HttpEngine` instance.
     * @throws IllegalArgumentException if `config.context` is null.
     */
    private fun buildEngine(): HttpEngine {
        val ctx = requireNotNull(config.context) {
            "AndroidEngineConfig.context must be set when using HttpEngine; prefer applicationContext."
        }.applicationContext
        return HttpEngine.Builder(ctx)
            .apply(config.httpEngineConfig)
            .build()
    }

    /**
     * Create an HttpURLConnection for the given URL string using the configured HttpEngine.
     *
     * @param urlString The URL to open, as a string.
     * @return An `HttpURLConnection` connected to the specified URL.
     */
    override operator fun invoke(urlString: String): HttpURLConnection {
        return engine.openConnection(URI.create(urlString).toURL()) as HttpURLConnection
    }
}
