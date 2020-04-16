/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import kotlin.reflect.*

@OptIn(KtorExperimentalLocationsAPI::class)
internal abstract class LocationsImpl(
    protected val routeService: LocationRouteService
) {
    protected val info: MutableMap<KClass<*>, LocationInfo> = HashMap()

    val registeredLocations: List<LocationInfo>
        get() = info.values.toList()

    fun getOrCreateInfo(locationClass: KClass<*>): LocationInfo {
        return info[locationClass] ?: createInfo(locationClass)
    }

    protected abstract fun createInfo(locationClass: KClass<*>): LocationInfo

    abstract fun <T : Any> instantiate(
        info: LocationInfo,
        allParameters: Parameters,
        klass: KClass<T>
    ): T

    abstract fun href(instance: Any): String

    abstract fun href(location: Any, builder: URLBuilder)
}
