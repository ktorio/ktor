package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

annotation class location(val path: String)

object Locations : LocationService()

open class LocationRoutingEntry<T>(val locationService: LocationService, val entry: RoutingEntry)
class LocationRouting(locationService: LocationService, routing: Routing) : LocationRoutingEntry<Any>(locationService, routing)


class LocationRoutingApplicationRequest<T>(val locationService: LocationService,
                                           request: ApplicationRequest,
                                           resolveResult: RoutingResolveResult) : RoutingApplicationRequest(request, resolveResult)

public fun Application.locations(body: LocationRouting.() -> Unit) {
    val routing = Routing()
    Locations.routing(routing).body()
    interceptRoute(routing)
}

inline fun LocationRoutingEntry<*>.location<reified T : Any>(noinline body: LocationRoutingEntry<T>.() -> Unit) {
    location(T::class, body)
}

inline fun LocationRoutingEntry<*>.get<reified T : Any>(noinline body: ApplicationResponse.(T) -> Unit) {
    location(T::class) {
        handle { location->
            respond {
                body(location)
                send()
            }
        }
    }
}

fun LocationRoutingEntry<*>.location<T>(data: KClass<T>, body: LocationRoutingEntry<T>.() -> Unit) {
    val dataEntry = locationService.createEntry(entry, data)
    LocationRoutingEntry<T>(locationService, dataEntry).body()
}

inline fun <reified T> LocationRoutingEntry<T>.handle(noinline body: LocationRoutingApplicationRequest<T>.(T) -> ApplicationRequestStatus) {
    return handle(T::class, body)
}

fun <T> LocationRoutingEntry<T>.handle(dataClass: KClass<T>, body: LocationRoutingApplicationRequest<T>.(T) -> ApplicationRequestStatus) {
    entry.addInterceptor(true) {
        val locationRequest = LocationRoutingApplicationRequest<T>(locationService, this, resolveResult)
        val location = locationService.resolve(dataClass, locationRequest)
        locationRequest.body(location)
    }
}

fun <T> LocationRoutingApplicationRequest<*>.sendRedirect(location: T): ApplicationRequestStatus {
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
