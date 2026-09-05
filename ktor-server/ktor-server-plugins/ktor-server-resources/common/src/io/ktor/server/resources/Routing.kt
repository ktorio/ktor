/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.resources

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.*

/**
 * Registers a route [body] for a resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.resource)
 */
public inline fun <reified T : Any> Route.resource(noinline body: Route.() -> Unit): Route {
    val serializer = serializer<T>()
    return resource(serializer, body)
}

/**
 * Registers a typed handler [body] for a `GET` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.get)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.get(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Get) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `OPTIONS` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.options)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.options(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Options) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `HEAD` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.head)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.head(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Head) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `POST` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.post)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.post(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Post) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `POST` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.post)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Route.post(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Route = post<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a typed handler [body] for a `PUT` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.put)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.put(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Put) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `PUT` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.put)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Route.put(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Route = put<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a typed handler [body] for a `DELETE` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.delete)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.delete(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Delete) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `PATCH` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.patch)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.patch(
    noinline body: suspend RoutingContext.(T) -> Unit
): Route {
    lateinit var builtRoute: Route
    resource<T> {
        builtRoute = method(HttpMethod.Patch) {
            handle(body)
        }
    }
    return builtRoute
}

/**
 * Registers a typed handler [body] for a `PATCH` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.patch)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Route.patch(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Route = patch<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a handler [body] for a resource defined by the [T] class.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.handle)
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.handle(
    noinline body: suspend RoutingContext.(T) -> Unit
) {
    val serializer = serializer<T>()
    handle(serializer, body)
}

@PublishedApi
internal val ResourceInstanceKey: AttributeKey<Any> = AttributeKey("ResourceInstance")

/**
 * Registers a route [body] for a resource defined by the [T] class.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.resource)
 *
 * @param serializer is used to decode the parameters of the request to an instance of the typed resource [T].
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 */
public fun <T : Any> Route.resource(
    serializer: KSerializer<T>,
    body: Route.() -> Unit
): Route {
    val resources = plugin(Resources)
    val path = resources.resourcesFormat.encodeToPathPattern(serializer)
    val queryParameters = resources.resourcesFormat.encodeToQueryParameters(serializer)
    val route = createRouteFromPath(path)

    return queryParameters.fold(route) { entry, query ->
        val selector = if (query.isOptional) {
            OptionalParameterRouteSelector(query.name)
        } else {
            ParameterRouteSelector(query.name)
        }
        entry.createChild(selector)
    }.apply(body)
}

/**
 * Registers a handler [body] for a resource defined by the [T] class.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.resources.handle)
 *
 * @param serializer is used to decode the parameters of the request to an instance of the typed resource [T].
 * @param body receives an instance of the typed resource [T] as the first parameter.
 *
 * If decoding the request parameters fails (for example a value can't be converted to the expected type),
 * a [io.ktor.server.plugins.BadRequestException] is thrown. Any other exception thrown while constructing
 * the resource instance, for example from an `init` block performing custom validation, propagates unchanged
 * so it can be handled by a `StatusPages` handler registered for its own type. This only applies when
 * [serializer] is the compiler-generated one for [T] (the usual case); a custom/delegating [serializer] only
 * gets this treatment for classes it decodes via `decoder.decodeSerializableValue(nested)`, not via
 * `nested.deserialize(decoder)` directly.
 */
public fun <T : Any> Route.handle(
    serializer: KSerializer<T>,
    body: suspend RoutingContext.(T) -> Unit
) {
    install(ResourceInstancePlugin) {
        this.serializer = serializer
    }

    handle {
        @Suppress("UNCHECKED_CAST")
        val resource = call.attributes[ResourceInstanceKey] as T
        body(resource)
    }
}

private class ResourceInstancePluginConfig {
    lateinit var serializer: KSerializer<*>
}

@OptIn(InternalAPI::class)
private val ResourceInstancePlugin = createRouteScopedPlugin("ResourceInstancePlugin", ::ResourceInstancePluginConfig) {
    val serializer = pluginConfig.serializer
    onCall { call ->
        val resources = call.application.plugin(Resources)
        try {
            val resource = resources.resourcesFormat
                .decodeFromParametersOrThrowConstructionFailure(serializer, call.parameters) as Any
            call.attributes.put(ResourceInstanceKey, resource)
        } catch (construction: ResourceConstructionFailure) {
            // Unwrapped before the check below, since `original` may coincidentally be a SerializationException.
            throw construction.original
        } catch (cause: Throwable) {
            when (cause) {
                is SerializationException,
                is ResourceSerializationException -> throw BadRequestException(
                    "Can't transform call to resource",
                    cause
                )

                else -> throw cause
            }
        }
    }
}
