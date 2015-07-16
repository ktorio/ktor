package org.jetbrains.ktor.components

import java.lang.reflect.*

public class InstanceDescriptor(val instance: Any) : ComponentDescriptor {

    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Type> = calculateClassRegistrations(instance.javaClass)

    override fun getDependencies(context: ValueResolveContext): Collection<Class<*>> = emptyList()
}
