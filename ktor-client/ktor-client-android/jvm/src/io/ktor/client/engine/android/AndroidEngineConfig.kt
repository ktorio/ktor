/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.engine.*
import java.net.*
import javax.net.ssl.*


/**
 * Configuration for [Android] client engine.
 */
public class AndroidEngineConfig : HttpClientEngineConfig() {
    /**
     * Max milliseconds to establish an HTTP connection - default 100 seconds.
     * A value of 0 represents infinite.
     */
    public var connectTimeout: Int = 100_000

    /**
     * Max milliseconds between TCP packets - default 100 seconds.
     * A value of 0 represents infinite.
     */
    public var socketTimeout: Int = 100_000


    /**
     * Https connection manipulator. inherited methods are not permitted.
     */
    public var sslManager: (HttpsURLConnection) -> Unit = {}

    /**
     * Engine specific request configuration.
     */
    public var requestConfig: HttpURLConnection.() -> Unit = {}
}
