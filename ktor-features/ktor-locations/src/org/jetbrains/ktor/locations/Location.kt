package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

annotation class location(val path: String)

fun RoutingEntry.locations() = application.feature(Locations)

inline fun <reified T : Any> RoutingEntry.location(noinline body: RoutingEntry.() -> Unit) {
    location(T::class, body)
}

inline fun <reified T : Any> RoutingEntry.get(noinline body: ApplicationCall.(T) -> Unit) {
    location(T::class) {
        method(HttpMethod.Get) {
            handle(body)
        }
    }
}

inline fun <reified T : Any> RoutingEntry.post(noinline body: ApplicationCall.(T) -> Unit) {
    location(T::class) {
        method(HttpMethod.Post) {
            handle(body)
        }
    }
}

fun <T : Any> RoutingEntry.location(data: KClass<T>, body: RoutingEntry.() -> Unit) {
    val entry = locations().createEntry(this, data)
    entry.body()
}

inline fun <reified T : Any> RoutingEntry.handle(noinline body: RoutingApplicationCall.(T) -> Unit) {
    return handle(T::class, body)
}

inline fun <T : Any> RoutingEntry.handle(dataClass: KClass<T>, crossinline body: RoutingApplicationCall.(T) -> Unit) {
    handle {
        val location = locations().resolve<T>(dataClass, this)
        body(location)
    }
}

fun <T : Any> RoutingApplicationCall.respondRedirect(location: T): ApplicationCallResult {
    return respondRedirect(route.locations().href(location))
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
