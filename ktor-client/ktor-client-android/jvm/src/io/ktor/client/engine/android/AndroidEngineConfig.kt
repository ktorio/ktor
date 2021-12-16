/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.engine.*
import java.net.*
import javax.net.ssl.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for [Android] client engine.
 */
public class AndroidEngineConfig : HttpClientEngineConfig() {
    /**
     * Max timeout to establish an HTTP connection - default 100 seconds.
     * A value of 0 represents infinite.
     */
    public var connectTimeout: Duration = 100.seconds

    /**
     * Max timeout between TCP packets - default 100 seconds.
     * A value of 0 represents infinite.
     */
    public var socketTimeout: Duration = 100.seconds

    /**
     * Https connection manipulator. inherited methods are not permitted.
     */
    public var sslManager: (HttpsURLConnection) -> Unit = {}

    /**
     * Engine specific request configuration.
     */
    public var requestConfig: HttpURLConnection.() -> Unit = {}
}
