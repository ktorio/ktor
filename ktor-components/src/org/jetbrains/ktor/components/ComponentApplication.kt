package org.jetbrains.ktor.components

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import kotlin.util.*

public open class ComponentApplication(config: ApplicationConfig) : Application(config) {
    val container = StorageComponentContainer("Application")
    val routing = Routing()
    val log = config.log.fork("Components")

    init {
        container.registerInstance(this)
        container.registerInstance(config)
        // TODO: instead of registering log itself, register component resolver, that can fork log for each component
        container.registerInstance(config.log)
        container.registerInstance(config.classLoader)

        container.registerInstance(routing)

        val introspectionTime = measureTimeMillis {
            config.classLoader
                    .scanForClasses("")
                    .filter { it.getAnnotation(Component::class.java) != null }
                    .forEach { container.registerSingleton(it) }
        }
        log.info("Introspection took $introspectionTime ms")

        val compositionTime = measureTimeMillis {
            container.compose()
        }
        log.info("Composition took $compositionTime ms")

        routing.installInto(this)
    }

    override fun dispose() {
        super.dispose()
        container.close()
    }

    fun routing(body: RoutingEntry.() -> Unit) = routing.apply(body)
}

@Retention(AnnotationRetention.RUNTIME)
annotation public class Component
