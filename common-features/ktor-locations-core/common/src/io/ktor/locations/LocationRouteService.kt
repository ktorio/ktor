/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlin.reflect.*

/**
 * Provides services for extracting routing information from a location class.
 */
@KtorExperimentalLocationsAPI
public interface LocationRouteService {
    /**
     * Retrieves routing information from a given [locationClass].
     * @return routing pattern, or null if a given class doesn't represent a route.
     */
    public fun findRoute(locationClass: KClass<*>): String?
}

/**
 * Implements [LocationRouteService] by extracting routing information from a [Location] annotation.
 */
@KtorExperimentalLocationsAPI
public class LocationAttributeRouteService : LocationRouteService {
    private inline fun <reified T : Annotation> KClass<*>.annotation(): T? {
        return annotationsList()?.singleOrNull { it is T } as T?
    }

    override fun findRoute(locationClass: KClass<*>): String? = locationClass.annotation<Location>()?.path
}
