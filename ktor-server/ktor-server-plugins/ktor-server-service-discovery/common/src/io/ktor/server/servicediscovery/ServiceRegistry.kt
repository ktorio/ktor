/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servicediscovery

import io.ktor.servicediscovery.*
import io.ktor.util.*
import io.ktor.utils.io.*

public interface ServiceRegistry<R : ServiceInstance> {
    public fun add(instance: R): Boolean
    public fun remove(instanceId: String): Boolean

    @InternalAPI
    public fun autoAddService()

    @InternalAPI
    public fun autoRemoveService()
}

public val ServiceRegistryKey: AttributeKey<ServiceRegistry<out ServiceInstance>> =
    AttributeKey<ServiceRegistry<out ServiceInstance>>("ServiceRegistryKey")

