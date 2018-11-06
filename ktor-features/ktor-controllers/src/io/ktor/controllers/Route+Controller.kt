@file:UseExperimental(KtorExperimentalControllersAPI::class)

package io.ktor.controllers

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.feature
import io.ktor.routing.Route
import io.ktor.routing.application

/**
 * Gets the [Application.controllers] feature
 */
val ApplicationCall.controllers: Controllers get() = application.controllers

/**
 * Gets the [Application.controllers] feature
 */
val Application.controllers: Controllers get() = feature(Controllers)

/**
 * Setup the given controller: class annotated with @RouteController.
 * It reads the objects annotations to determine the path to be and the methods to set up.
 */
@UseExperimental(KtorExperimentalControllersAPI::class)
fun <T : Any> Route.setupController(controller: T) {
    application.controllers.setupController(this, controller)
}

/**
 * Setup the given controller: class annotated with @RouteController in the given path.
 * It reads the objects annotations to determine the path to be and the methods to set up.
 */
@UseExperimental(KtorExperimentalControllersAPI::class)
fun <T : Any> Route.setupController(path: String, controller: T) {
    application.controllers.setupController(this, path, controller)
}
