/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.resources

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.*
import kotlin.native.concurrent.*

/**
 * Registers a route [body] for a resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 */
public inline fun <reified T : Any> Route.resource(noinline body: Route.() -> Unit): Route {
    val serializer = serializer<T>()
    return resource(serializer, body)
}

/**
 * Registers a typed handler [body] for a `GET` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.get(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Get) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `OPTIONS` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.options(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Options) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `HEAD` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.head(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Head) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `POST` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.post(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Post) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `PUT` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.put(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Put) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `DELETE` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.delete(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Delete) {
            handle(body)
        }
    }
}

/**
 * Registers a typed handler [body] for a `PATCH` resource defined by the [T] class.
 *
 * Class [T] **must** be annotated with [io.ktor.resources.Resource].
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.patch(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
): Route {
    return resource<T> {
        method(HttpMethod.Patch) {
            handle(body)
        }
    }
}

/**
 * Registers a handler [body] for a resource defined by the [T] class.
 *
 * @param body receives an instance of the typed resource [T] as the first parameter.
 */
public inline fun <reified T : Any> Route.handle(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
) {
    val serializer = serializer<T>()
    handle(serializer, body)
}

@PublishedApi
internal val ResourceInstanceKey: AttributeKey<Any> = AttributeKey("ResourceInstance")

@PublishedApi
internal fun <T : Any> Route.resource(
    serializer: KSerializer<T>,
    body: Route.() -> Unit
): Route {
    val resources = application.plugin(Resources)
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

@PublishedApi
internal fun <T : Any> Route.handle(
    serializer: KSerializer<T>,
    body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
) {
    intercept(ApplicationCallPipeline.Plugins) {
        val resources = application.plugin(Resources)
        try {
            val resource = resources.resourcesFormat.decodeFromParameters<T>(serializer, call.parameters)
            call.attributes.put(ResourceInstanceKey, resource)
        } catch (cause: Throwable) {
            throw BadRequestException("Can't transform call to resource", cause)
        }
    }

    handle {
        @Suppress("UNCHECKED_CAST")
        val resource = call.attributes[ResourceInstanceKey] as T
        body(resource)
    }
}
