package org.jetbrains.ktor.components

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import kotlin.util.*

public open class ComponentApplication(config: ApplicationConfig) : Application(config) {
    val container = StorageComponentContainer("Application")
    val routing = Routing()

    init {
        container.registerInstance(this)
        container.registerInstance(config)
        container.registerInstance(config.log)
        container.registerInstance(config.classLoader)

        container.registerInstance(routing)

        val introspectionTime = measureTimeMillis {
            config.classLoader
                    .scanForClasses("")
                    .filter { it.getAnnotation(Component::class.java) != null }
                    .forEach { container.registerSingleton(it) }
        }
        config.log.info("Introspection took $introspectionTime ms")

        val compositionTime = measureTimeMillis {
            container.compose()
        }
        config.log.info("Composition took $compositionTime ms")

        routing.installInto(this)
    }

    fun routing(body: RoutingEntry.() -> Unit) = routing.apply(body)
}

@Retention(AnnotationRetention.RUNTIME)
annotation public class Component
