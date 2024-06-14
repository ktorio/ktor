/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.resources

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.*

/**
 * Registers a route [body] for a resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 */
public inline fun <reified T : Any> Routing.resource(noinline body: Routing.() -> Unit): Routing {
    val serializer = serializer<T>()
    return resource(serializer, body)
}

/**
 * Registers a typed handler [body] for a `GET` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.get(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Get) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `OPTIONS` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.options(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Options) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `HEAD` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.head(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Head) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `POST` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.post(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Post) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `POST` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Routing.post(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Routing = post<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a typed handler [body] for a `PUT` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.put(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Put) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `PUT` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Routing.put(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Routing = put<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a typed handler [body] for a `DELETE` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.delete(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Delete) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `PATCH` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.patch(
    noinline body: suspend RoutingContext.(T) -> Unit
): Routing {
    lateinit var builtRouting: Routing
    resource<T> {
        builtRouting = method(HttpMethod.Patch) {
            handle(body)
        }
    }
    return builtRouting
}

/**
 * Registers a typed handler [body] for a `PATCH` resource defined by the [T] class.
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter
 * and typed request body [R] as second parameter.
 */
public inline fun <reified T : Any, reified R : Any> Routing.patch(
    noinline body: suspend RoutingContext.(T, R) -> Unit,
): Routing = patch<T> { resource ->
    body(resource, call.receive())
}

/**
 * Registers a handler [body] for a resource defined by the [T] class.
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Routing.handle(
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
 * @param serializer is used to decode the parameters of the request to an instance of the typed resource [T].
 *
 * A class [T] **must** be annotated with [io.ktor.resources.Resource].
 */
public fun <T : Any> Routing.resource(
    serializer: KSerializer<T>,
    body: Routing.() -> Unit
): Routing {
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
 * @param serializer is used to decode the parameters of the request to an instance of the typed resource [T].
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public fun <T : Any> Routing.handle(
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

private val ResourceInstancePlugin = createRouteScopedPlugin("ResourceInstancePlugin", ::ResourceInstancePluginConfig) {
    val serializer = pluginConfig.serializer
    onCall { call ->
        val resources = call.application.plugin(Resources)
        try {
            val resource = resources.resourcesFormat.decodeFromParameters(serializer, call.parameters) as Any
            call.attributes.put(ResourceInstanceKey, resource)
        } catch (cause: Throwable) {
            throw BadRequestException("Can't transform call to resource", cause)
        }
    }
}
