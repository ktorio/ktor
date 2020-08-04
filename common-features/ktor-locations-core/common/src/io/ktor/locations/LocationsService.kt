/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

public abstract class LocationsService
@InternalAPI
constructor(
    routeService: LocationRouteService,
    conversionService: () -> ConversionService?,
    logger: (String) -> Unit,
    module: SerializersModule,
    compatibilityMode: Boolean
) {

    private val implementation: LocationsImpl = createImpl(
        conversionService, routeService, logger, module, compatibilityMode
    )

    /**
     * All locations registered at the moment (Immutable list).
     */
    @KtorExperimentalLocationsAPI
    public val registeredLocations: List<LocationInfo>
        get() = implementation.registeredLocations

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> resolve(locationClass: KClass<T>, parameters: Parameters): T {
        val info = implementation.getOrCreateInfo(locationClass)
        return implementation.instantiate(info, parameters, locationClass)
    }

    /**
     * Resolves [parameters] to an instance of specified [T].
     */
    @KtorExperimentalLocationsAPI
    public inline fun <reified T : Any> resolve(parameters: Parameters): T {
        return resolve(T::class, parameters)
    }

    /**
     * Constructs the url for [location].
     *
     * The class of [location] instance **must** be annotated with [Location].
     */
    public fun href(location: Any): String {
        return implementation.href(location)
    }

    public fun href(location: Any, builder: URLBuilder) {
        implementation.href(location, builder)
    }

    protected fun getOrCreateInfo(locationClass: KClass<*>): LocationInfo {
        return implementation.getOrCreateInfo(locationClass)
    }

    /**
     * Location service configuration
     */
    public open class Configuration @InternalAPI constructor() {
        /**
         * [SerializersModule] to be used in URL serialization
         */
        @KtorExperimentalLocationsAPI
        public var module: SerializersModule = EmptySerializersModule
    }
}
