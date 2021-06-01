/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")
@file:OptIn(KtorExperimentalLocationsAPI::class)

package io.ktor.locations

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.reflect.*

/**
 * API marked with this annotation is experimental and is not guaranteed to be stable.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This locations API is experimental. It could be changed or removed in future releases."
)
@Experimental(level = Experimental.Level.WARNING)
public annotation class KtorExperimentalLocationsAPI

/**
 * Annotation for classes that will act as typed routes.
 * @property path the route path, including class property names wrapped with curly braces.
 */
@KtorExperimentalLocationsAPI
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
public annotation class Location(val path: String)

/**
 * Gets the [Application.locations] feature
 */
@KtorExperimentalLocationsAPI
public val RoutingCallContext.locations: Locations
    get() = call.call.locations

/**
 * Gets the [Application.locations] feature
 */
@KtorExperimentalLocationsAPI
public val PipelineContext<Unit, ApplicationCall>.locations: Locations
    get() = call.application.locations

/**
 * Gets the [Application.locations] feature
 */
@KtorExperimentalLocationsAPI
public val RoutingCall.locations: Locations
    get() = call.locations

/**
 * Gets the [Application.locations] feature
 */
@KtorExperimentalLocationsAPI
public val ApplicationCall.locations: Locations
    get() = application.locations

/**
 * Gets the [Application.locations] feature
 */
@KtorExperimentalLocationsAPI
public val Application.locations: Locations
    get() = feature(Locations)

/**
 * Renders link to a [location] using current installed locations service
 * @throws MissingApplicationFeatureException is no locations feature installed
 */
@KtorExperimentalLocationsAPI
public fun RoutingCallContext.href(location: Any): String {
    return call.call.href(location)
}

/**
 * Renders link to a [location] using current installed locations service
 * @throws MissingApplicationFeatureException is no locations feature installed
 */
@KtorExperimentalLocationsAPI
public fun PipelineContext<Unit, ApplicationCall>.href(location: Any): String {
    return call.href(location)
}

/**
 * Renders link to a [location] using current installed locations service
 * @throws MissingApplicationFeatureException is no locations feature installed
 */
@KtorExperimentalLocationsAPI
public fun ApplicationCall.href(location: Any): String {
    return application.locations.href(location)
}

/**
 * Registers a route [body] for a location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.location(noinline body: RoutingBuilder.() -> Unit): RoutingBuilder {
    return location(T::class, body)
}

/**
 * Registers a typed handler [body] for a `GET` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.get(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Get) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `OPTIONS` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.options(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Options) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `HEAD` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.head(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Head) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `POST` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.post(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Post) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `PUT` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.put(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Put) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `DELETE` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.delete(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Delete) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `PATCH` location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [T] as first parameter.
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.patch(
    noinline body: suspend RoutingCallContext.(T) -> Unit
): RoutingBuilder {
    return location(T::class) {
        method(HttpMethod.Patch) {
            handle(body)
        }
    }
}

/**
 * Registers a route [body] for a location defined by class [data].
 *
 * Class [data] **must** be annotated with [Location].
 */
@KtorExperimentalLocationsAPI
public fun <T : Any> RoutingBuilder.location(data: KClass<T>, body: RoutingBuilder.() -> Unit): RoutingBuilder {
    check(this is Route)
    val entry = application.locations.createEntry(this, data)
    return entry.apply(body)
}

/**
 * Registers a handler [body] for a location defined by class [T].
 *
 * Class [T] **must** be annotated with [Location].
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingBuilder.handle(
    noinline body: suspend RoutingCallContext.(T) -> Unit
) {
    return handle(T::class, body)
}

/**
 * Registers a handler [body] for a location defined by class [dataClass].
 *
 * Class [dataClass] **must** be annotated with [Location].
 *
 * @param body receives an instance of typed location [dataClass] as first parameter.
 */
@KtorExperimentalLocationsAPI
public fun <T : Any> RoutingBuilder.handle(
    dataClass: KClass<T>,
    body: suspend RoutingCallContext.(T) -> Unit
) {
    check(this is Route)
    intercept(ApplicationCallPipeline.Features) {
        call.attributes.put(LocationInstanceKey, locations.resolve<T>(dataClass, call))
    }

    handle {
        @Suppress("UNCHECKED_CAST")
        val location = call.attributes[LocationInstanceKey] as T

        body(location)
    }
}

/**
 * Retrieves the current call's location or fails if it is not available (request is not handled by a location class),
 * or not yet available (invoked too early before the locations feature takes place).
 *
 * Despite of the name, the function fails if no location found. This is why it's deprecated.
 */
@KtorExperimentalLocationsAPI
@Deprecated("Use location function instead.", ReplaceWith("this.location<T>()"), level = DeprecationLevel.ERROR)
public inline fun <reified T : Any> ApplicationCall.locationOrNull(): T = location()

/**
 * Retrieves the current call's location or fails if it is not available (request is not handled by a location class),
 * or not yet available (invoked too early before the locations feature takes place).
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> RoutingCall.location(): T = call.location()

/**
 * Retrieves the current call's location or fails if it is not available (request is not handled by a location class),
 * or not yet available (invoked too early before the locations feature takes place).
 */
@KtorExperimentalLocationsAPI
public inline fun <reified T : Any> ApplicationCall.location(): T = locationOrThrow(T::class)

@PublishedApi
internal fun <T : Any> ApplicationCall.locationOrNull(type: KClass<T>): T =
    attributes.getOrNull(LocationInstanceKey)?.let { instance ->
        type.cast(instance)
    } ?: error("Location instance is not available for this call.)")

@PublishedApi
internal fun <T : Any> ApplicationCall.locationOrThrow(type: KClass<T>): T =
    attributes.getOrNull(LocationInstanceKey)?.let { instance ->
        type.cast(instance)
    } ?: error("Location instance is not available for this call.)")

private val LocationInstanceKey = AttributeKey<Any>("LocationInstance")

private fun <T : Any> KClass<T>.cast(instance: Any): T {
    return javaObjectType.cast(instance)
}
