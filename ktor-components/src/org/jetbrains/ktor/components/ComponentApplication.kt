package org.jetbrains.ktor.components

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.components.*
import org.jetbrains.ktor.routing.*
import kotlin.util.*

public open class ComponentApplication(config: ApplicationConfig) : Application(config) {
    val container = StorageComponentContainer("Application")

    init {
        container.registerInstance(this)
        container.registerInstance(config)
        container.registerInstance(config.log)
        container.registerInstance(config.classLoader)

        val routing = Routing()
        container.registerInstance(routing)

        val introspectionTime = measureTimeMillis {
            config.classLoader
                    .scanForClasses("")
                    .filter { it.getAnnotation(javaClass<component>()) != null }
                    .forEach { container.registerSingleton(it) }
        }
        config.log.info("Introspection took $introspectionTime ms")

        val compositionTime = measureTimeMillis {
            container.compose()
        }
        config.log.info("Composition took $compositionTime ms")

        routing.installInto(this)
    }
}

annotation(AnnotationRetention.RUNTIME)
public class component
