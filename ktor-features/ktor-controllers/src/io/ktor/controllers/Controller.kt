package io.ktor.controllers

import io.ktor.application.Application
import io.ktor.application.ApplicationFeature
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.AttributeKey
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

@KtorExperimentalControllersAPI
class Controllers private constructor(
        private val application: Application,
        private val configuration: Configuration
) {

    private val paramDetectors: List<ParamDetector> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
                *(configuration.customParamDetectors.toTypedArray()),
                ParamDetectors.CallDetector,
                ParamDetectors.PathParamDetector,
                ParamDetectors.QueryParamDetector
        )
    }

    private val resultDetectors: List<ResultDetector> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
                ResultDetectors.UnitDetector,
                ResultDetectors.StringDetector,
                ResultDetectors.ObjectDetector
        )
    }

    /**
     * Setup the given controller: class annotated with @RouteController.
     * It reads the objects annotations to determine the path to be and the methods to set up.
     */
    fun <T : Any> setupController(route: Route, controller: T) {
        val kclass = controller::class
        val controllerAnnotation = kclass.findAnnotation<RouteController>()
                ?: error("Controller must be annotated with @RouteController")
        route.setupFunctions(controllerAnnotation.path, controller, kclass)
    }

    /**
     * Setup the given controller: class annotated with @RouteController in the given path.
     * It reads the objects annotations to determine the path to be and the methods to set up.
     */
    fun <T : Any> setupController(route: Route, path: String, controller: T) {
        val kclass = controller::class
        val controllerAnnotation = kclass.findAnnotation<RouteController>()
                ?: error("Controller must be annotated with @RouteController")
        route.route(path) { setupFunctions(controllerAnnotation.path, controller, kclass) }
    }

    private fun <T : Any> Route.setupFunctions(path: String, controller: Any, kclass: KClass<out T>) {
        if (path.isNotEmpty()) {
            route(path) { setupFunctions(controller, kclass) }
        } else {
            setupFunctions(controller, kclass)
        }
    }

    private fun <T : Any> Route.setupFunctions(controller: Any, kclass: KClass<out T>) {
        for (function in kclass.functions) {
            for (detector in FunctionDetectors.AllDetectors) {
                val functionMapping = detector.detect(controller, function, paramDetectors, resultDetectors)
                if (functionMapping != null) {
                    functionMapping.invoke(this)
                    break
                }
            }
        }
    }

    /**
     * Configuration for [Controllers] feature
     */
    class Configuration {
        val customParamDetectors = mutableListOf<ParamDetector>()
    }

    /**
     * Installable feature for [Controllers].
     */
    companion object Feature : ApplicationFeature<Application, Controllers.Configuration, Controllers> {
        override val key: AttributeKey<Controllers> = AttributeKey("Controller")
        override fun install(pipeline: Application, configure: Controllers.Configuration.() -> Unit): Controllers {
            val configuration = Controllers.Configuration().apply(configure)
            return Controllers(pipeline, configuration)
        }
    }
}
