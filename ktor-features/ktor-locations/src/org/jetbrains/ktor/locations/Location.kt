package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

annotation class location(val path: String)

object Locations : LocationService(DefaultConversionService())

val locationServiceKey = Routing.Key<LocationService>("LocationService")

public fun Application.locations(body: Routing.() -> Unit) {
    val routing = Routing()
    routing.withLocations(Locations, body)
    routing.installInto(this)
}

public fun Routing.withLocations(locations: LocationService, body: Routing.() -> Unit) {
    addService(locationServiceKey, locations)
    body()
}

inline fun <reified T : Any> RoutingEntry.location(noinline body: RoutingEntry.() -> Unit) {
    location(T::class, body)
}

inline fun <reified T : Any> RoutingEntry.get(noinline body: ApplicationCall.(T) -> ApplicationCallResult) {
    location(T::class) {
        method(HttpMethod.Get) {
            handle<T> { location -> body(location) }
        }
    }
}

inline fun <reified T : Any> RoutingEntry.post(noinline body: ApplicationCall.(T) -> ApplicationCallResult) {
    location(T::class) {
        method(HttpMethod.Post) {
            handle<T> { location -> body(location) }
        }
    }
}

fun <T : Any> RoutingEntry.location(data: KClass<T>, body: RoutingEntry.() -> Unit) {
    val locationService = getService(locationServiceKey)
    val entry = locationService.createEntry(this, data)
    entry.body()
}

inline fun <reified T : Any> RoutingEntry.handle(noinline body: RoutingApplicationCall.(T) -> ApplicationCallResult) {
    return handle(T::class, body)
}

fun <T : Any> RoutingEntry.handle(dataClass: KClass<T>, body: RoutingApplicationCall.(T) -> ApplicationCallResult) {
    handle {
        val locationService = getService(locationServiceKey)
        val location = locationService.resolve<T>(dataClass, this)
        body(location)
    }
}

fun <T : Any> RoutingApplicationCall.sendRedirect(location: T): ApplicationCallResult {
    val locationService = resolveResult.entry.getService(locationServiceKey)
    return response.sendRedirect(locationService.href(location))
}

// ------- app ---------

// --- discovery ---

// annotation class get(val contentType: String = "*/*", vararg val param: RoutingParam)

/*
annotation class RoutingParam(val name: String, val value: String)

*/
/*element*//*
 class SomeService(val routing: LocationRouting) {

    get(contentType = "text/html")
    fun RoutingApplicationRequest.user(profile: UserLocations.profile) {
        val userId = profile.id
        val cacheControl = cacheControl()
        respond {

            send()
        }
    }
}*/
