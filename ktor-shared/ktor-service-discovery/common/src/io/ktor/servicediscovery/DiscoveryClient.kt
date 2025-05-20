/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.servicediscovery

import io.ktor.util.*

public interface DiscoveryClient<T : ServiceInstance> {
    public suspend fun getInstances(serviceId: String): List<T>
    public suspend fun getServiceIds(): List<String>
}

public val DiscoveryClientKey: AttributeKey<DiscoveryClient<out ServiceInstance>> =
    AttributeKey<DiscoveryClient<out ServiceInstance>>("DiscoveryClientKey")
