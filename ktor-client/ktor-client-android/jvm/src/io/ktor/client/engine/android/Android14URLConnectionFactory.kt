/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.net.http.*
import java.net.*

//@RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
internal class AndroidNetHttpEngineFactory(private val config: AndroidEngineConfig) : URLConnectionFactory {
    private val engine = buildEngine()

    fun buildEngine(): HttpEngine {
        return HttpEngine.Builder(config.context!!)
            .apply(config.httpEngineConfig)
            .build()
    }

    override operator fun invoke(urlString: String): HttpURLConnection {
        return engine.openConnection(URI.create(urlString).toURL()) as HttpURLConnection
    }
}
