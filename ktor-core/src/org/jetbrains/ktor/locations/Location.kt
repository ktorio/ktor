package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

annotation class location(val path: String)

object Locations : LocationService(DefaultConversionService())

val locationServiceKey = Routing.Key<LocationService>("LocationService")

public fun Application.locations(body: Routing.() -> Unit) {
    val routing = Routing()
    routing.withLocations(Locations, body)
    interceptRoute(routing)
}

public fun Routing.withLocations(locations: LocationService, body: Routing.() -> Unit) {
    addService(locationServiceKey, locations)
    body()
}

inline fun RoutingEntry.location<reified T : Any>(noinline body: RoutingEntry.() -> Unit) {
    location(T::class, body)
}

inline fun RoutingEntry.get<reified T : Any>(noinline body: ApplicationResponse.(T) -> Unit) {
    location(T::class) {
        get {
            handle<T> { location ->
                respond {
                    body(location)
                    send()
                }
            }
        }
    }
}

fun RoutingEntry.location<T>(data: KClass<T>, body: RoutingEntry.() -> Unit) {
    val locationService = getService(locationServiceKey)
    val entry = locationService.createEntry(this, data)
    entry.body()
}

inline fun <reified T> RoutingEntry.handle(noinline body: RoutingApplicationRequest.(T) -> ApplicationRequestStatus) {
    return handle(T::class, body)
}

fun <T> RoutingEntry.handle(dataClass: KClass<T>, body: RoutingApplicationRequest.(T) -> ApplicationRequestStatus) {
    addInterceptor(true) {
        val locationService = getService(locationServiceKey)
        val location = locationService.resolve<T>(dataClass, this)
        body(location)
    }
}

fun <T> RoutingApplicationRequest.sendRedirect(location: T): ApplicationRequestStatus {
    val locationService = resolveResult.entry.getService(locationServiceKey)
    return sendRedirect(locationService.href(location))
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
