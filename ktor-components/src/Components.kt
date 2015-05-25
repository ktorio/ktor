package org.jetbrains.container

import java.io.Closeable
import java.lang.reflect.Method

public class InstanceComponentDescriptor(val instance: Any) : ComponentDescriptor {

    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Class<*>> {
        return getRegistrationsForClass(instance.javaClass)
    }

    override fun getDependencies(context: ValueResolveContext): Collection<Class<*>> = emptyList()
}

public class ProviderComponentDescriptor(val instance: Any, val method: Method) : ComponentDescriptor {
    override fun getValue(): Any = method.invoke(instance)
    override fun getRegistrations(): Iterable<Class<*>> {
        return getRegistrationsForClass(method.getReturnType())
    }

    // TODO: method parameters could be dependencies, for now we assume no params
    override fun getDependencies(context: ValueResolveContext): Collection<Class<*>> = emptyList()
}

