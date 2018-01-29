package io.ktor.locations

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.routing.*
import kotlin.reflect.*

annotation class Location(val path: String)

@Deprecated("Use Location instead", replaceWith = ReplaceWith("Location"))
typealias location = Location

val PipelineContext<Unit, ApplicationCall>.locations get() = call.application.locations
val ApplicationCall.locations get() = application.locations
val Application.locations get() = feature(Locations)

@RoutingDsl
inline fun <reified T : Any> Route.location(noinline body: Route.() -> Unit): Route {
    return location(T::class, body)
}

@RoutingDsl
inline fun <reified T : Any> Route.get(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Get) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.options(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Options) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.head(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Head) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.post(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Post) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.put(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Put) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.delete(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Delete) {
            handle(body)
        }
    }
}

@RoutingDsl
inline fun <reified T : Any> Route.patch(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit): Route {
    return location(T::class) {
        method(HttpMethod.Patch) {
            handle(body)
        }
    }
}

@RoutingDsl
fun <T : Any> Route.location(data: KClass<T>, body: Route.() -> Unit): Route {
    val entry = application.locations.createEntry(this, data)
    return entry.apply(body)
}

@RoutingDsl
inline fun <reified T : Any> Route.handle(noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit) {
    return handle(T::class, body)
}

@RoutingDsl
fun <T : Any> Route.handle(dataClass: KClass<T>, body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit) {
    handle {
        val location = locations.resolve<T>(dataClass, call)
        body(location)
    }
}
