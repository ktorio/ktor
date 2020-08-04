/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.modules.*

internal class StandaloneLocationsService(
    routeService: LocationRouteService,
    conversionService: () -> ConversionService?,
    logger: (String) -> Unit,
    module: SerializersModule,
    compatibilityMode: Boolean
) : LocationsService(routeService, conversionService, logger, module, compatibilityMode)

/**
 * Creates a standalone [LocationsService] instance
 * that is not tied to any container (such as a ktor server application).
 */
@KtorExperimentalLocationsAPI
public fun standaloneLocationService(
    configure: LocationsService.Configuration.() -> Unit
): LocationsService {
    val config = LocationsService.Configuration().apply(configure)
    return StandaloneLocationsService(
        LocationAttributeRouteService(),
        { null },
        {},
        config.module,
        false
    )
}
