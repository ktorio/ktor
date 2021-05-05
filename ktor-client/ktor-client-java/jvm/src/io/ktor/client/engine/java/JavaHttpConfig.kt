/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import java.net.http.*

/**
 * Configuration for [Java] client engine.
 */
public class JavaHttpConfig : HttpClientEngineConfig() {

    internal var config: HttpClient.Builder.() -> Unit = {
        followRedirects(HttpClient.Redirect.NEVER)
    }

    /**
     * Configure [HttpClient] using [HttpClient.Builder].
     */
    public fun config(block: HttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }
}
