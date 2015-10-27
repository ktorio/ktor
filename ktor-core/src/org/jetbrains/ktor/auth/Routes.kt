package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*

fun <T: Any> RoutingEntry.auth(credentialsExtractor: RoutingApplicationRequestContext.() -> T?,
                               validator: (T) -> Boolean,
                               onSuccess: RoutingApplicationRequestContext.(T, RoutingApplicationRequestContext.() -> ApplicationRequestStatus) -> ApplicationRequestStatus,
                               onFailed: RoutingApplicationRequestContext.(RoutingApplicationRequestContext.() -> ApplicationRequestStatus) -> ApplicationRequestStatus) {

    intercept { next ->
        val credentials = credentialsExtractor()
        if (credentials == null || !validator(credentials)) {
            onFailed(next)
        } else {
            onSuccess(credentials, next)
        }
    }
}
