/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.servicediscovery

import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * Represents an instance of a service in a discovery system
 */
public interface ServiceInstance {
    /**
     * Unique identifier of the service instance
     */
    public val instanceId: String

    /**
     * Logical service name (e.g., "user-service")
     */
    public val serviceId: String

    public val host: String

    public val port: Int

    public val url: Url
        get() = URLBuilder().apply {
            host = this@ServiceInstance.host
            port = this@ServiceInstance.port
        }.build()

    /**
     * Arbitrary key-value data (e.g., version, zone)
     */
    public val metadata: Map<String, String>
}
