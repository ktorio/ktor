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

    private fun buildEngine(): HttpEngine {
        val ctx = requireNotNull(config.context) {
            "AndroidEngineConfig.context must be set when using HttpEngine; prefer applicationContext."
        }.applicationContext
        return HttpEngine.Builder(ctx)
            .apply(config.httpEngineConfig)
            .build()
    }

    override operator fun invoke(urlString: String): HttpURLConnection {
        return engine.openConnection(URI.create(urlString).toURL()) as HttpURLConnection
    }
}
