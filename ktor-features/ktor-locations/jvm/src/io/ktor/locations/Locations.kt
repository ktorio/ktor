/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

/**
 * Ktor feature that allows to handle and construct routes in a typed way.
 *
 * You have to create data classes/objects representing parameterized routes and annotate them with [Location].
 * Then you can register sub-routes and handlers for those locations and create links to them
 * using [Locations.href].
 */
public open class Locations
@Deprecated(
    "Instantiating or inheriting this class is deprecated. Use feature instead.",
    level = DeprecationLevel.ERROR
)
constructor(
    application: Application,
    routeService: LocationRouteService,
    compatibilityMode: Boolean
) : LocationsService(
    routeService,
    application::conversionService,
    { application.log.warn(it) },
    EmptySerializersModule,
    compatibilityMode
) {
    @Deprecated(
        "Instantiating or inheriting this class is deprecated. Use feature instead.",
        level = DeprecationLevel.ERROR
    )
    @Suppress("DEPRECATION_ERROR")
    public constructor(
        application: Application,
        routeService: LocationRouteService
    ) : this(application, routeService, true)

    /**
     * Creates Locations service extracting path information from @Location annotation
     */
    @Deprecated(
        "Instantiating or inheriting this class is deprecated. Use feature instead.",
        level = DeprecationLevel.ERROR
    )
    @Suppress("DEPRECATION_ERROR")
    public constructor(application: Application) : this(application, LocationAttributeRouteService())

    /**
     * Resolves parameters in a [call] to an instance of specified [locationClass].
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> resolve(locationClass: KClass<T>, call: ApplicationCall): T {
        try {
            return resolve(locationClass, call.parameters)
        } catch (cause: MissingParameterException) {
            throw MissingRequestParameterException(cause.propertyName)
        } catch (cause: URLDecodingException) {
            throw BadRequestException(cause.message, cause)
        }
    }

    /**
     * Resolves parameters in a [call] to an instance of specified [T].
     */
    @KtorExperimentalLocationsAPI
    public inline fun <reified T : Any> resolve(call: ApplicationCall): T {
        return resolve(T::class, call)
    }

    @OptIn(KtorExperimentalLocationsAPI::class)
    private fun createEntry(parent: Route, info: LocationInfo): Route {
        val hierarchyEntry = info.parent?.let { createEntry(parent, it) } ?: parent
        return hierarchyEntry.createRouteFromPath(info.path)
    }

    /**
     * Creates all necessary routing entries to match specified [locationClass].
     */
    public fun createEntry(parent: Route, locationClass: KClass<*>): Route {
        val info = getOrCreateInfo(locationClass)
        val pathRoute = createEntry(parent, info)

        @OptIn(KtorExperimentalLocationsAPI::class)
        return info.queryParameters.fold(pathRoute) { entry, query ->
            val selector = if (query.isOptional)
                OptionalParameterRouteSelector(query.name)
            else
                ParameterRouteSelector(query.name)
            entry.createChild(selector)
        }
    }

    /**
     * Configuration for [Locations].
     */
    public class Configuration {
        /**
         * Specifies an alternative routing service. Default is [LocationAttributeRouteService].
         */
        @KtorExperimentalLocationsAPI
        public var routeService: LocationRouteService? = null

        /**
         * Turns the old locations implementation on.
         * The old implementation doesn't rely on kotlinx.serialization and works exactly as before.
         */
        public var compatibilityMode: Boolean = true
    }

    /**
     * Installable feature for [Locations].
     */
    public companion object Feature : ApplicationFeature<Application, Configuration, Locations> {
        override val key: AttributeKey<Locations> = AttributeKey("Locations")

        @OptIn(KtorExperimentalLocationsAPI::class)
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Locations {
            val configuration = Configuration().apply(configure)
            val routeService = configuration.routeService ?: LocationAttributeRouteService()
            @Suppress("DEPRECATION_ERROR")
            return Locations(pipeline, routeService, configuration.compatibilityMode)
        }
    }
}
