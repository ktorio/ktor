package ktor.application

import kotlin.properties.Delegates
import java.io.Closeable

class ContainerConsistencyException(message: String) : Exception(message)

public trait IComponentContainer {
    fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext
}

object DynamicComponentDescriptor : ComponentDescriptor {
    override fun getRegistrations(): Iterable<Class<*>> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

object UnidentifiedComponentDescriptor : ComponentDescriptor {
    override fun getRegistrations(): Iterable<Class<*>> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

public class ComponentContainer(id: String) : IComponentContainer, Closeable {
    val unknownContext by Delegates.lazy { ComponentResolveContext(this, DynamicComponentDescriptor) }
    val componentStorage = ComponentStorage(id)

    override fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext {
        if (requestingDescriptor == DynamicComponentDescriptor) // cache unknown component descriptor
            return unknownContext
        return ComponentResolveContext(this, requestingDescriptor)
    }

    fun compose(): ComponentContainer {
        componentStorage.compose()
        return this
    }

    override fun close() = componentStorage.dispose()

    public fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor? {
        return componentStorage.resolve(request, context)
    }

    public fun resolveMultiple(request: Class<*>, context: ValueResolveContext): Iterable<ValueDescriptor> {
        return componentStorage.resolveMultiple(request, context)
    }

    public fun registerDescriptors(descriptors: List<ComponentDescriptor>): ComponentContainer {
        componentStorage.registerDescriptors(descriptors)
        return this
    }

}

public fun ComponentContainer.register(klass: Class<*>, lifetime: ComponentLifetime = ComponentLifetime.Singleton): ComponentContainer =
        when (lifetime) {
            ComponentLifetime.Singleton -> registerSingleton(klass)
            ComponentLifetime.Transient -> registerTransient(klass)
        }


public fun ComponentContainer.registerSingleton(klass: Class<*>): ComponentContainer {
    return registerDescriptors(listOf(SingletonTypeComponentDescriptor(this, klass)))
}

public fun ComponentContainer.registerTransient(klass: Class<*>): ComponentContainer {
    return registerDescriptors(listOf(TransientTypeComponentDescriptor(this, klass)))
}

public fun ComponentContainer.registerInstance(instance: Any): ComponentContainer {
    return registerDescriptors(listOf(ObjectComponentDescriptor(instance)))
}

public inline fun <reified T> ComponentContainer.register(lifetime: ComponentLifetime = ComponentLifetime.Singleton): ComponentContainer =
        if (lifetime == ComponentLifetime.Singleton) registerSingleton<T>()
        else if (lifetime == ComponentLifetime.Transient) registerTransient<T>()
        else throw IllegalStateException("Unknown lifetime: ${lifetime}}")

public inline fun <reified T> ComponentContainer.registerSingleton(): ComponentContainer {
    return registerDescriptors(listOf(SingletonTypeComponentDescriptor(this, javaClass<T>())))
}

public inline fun <reified T>  ComponentContainer.registerTransient(): ComponentContainer {
    return registerDescriptors(listOf(TransientTypeComponentDescriptor(this, javaClass<T>())))
}

public inline fun <reified T> ComponentContainer.resolve(context: ValueResolveContext): ValueDescriptor? {
    return resolve(javaClass<T>(), context)
}

public inline fun <reified T> ComponentContainer.resolveMultiple(context: ValueResolveContext): Iterable<ValueDescriptor> {
    return resolveMultiple(javaClass<T>(), context)
}
