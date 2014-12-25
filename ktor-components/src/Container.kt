package ktor.application

import kotlin.properties.Delegates
import java.io.Closeable

class ContainerConsistencyException(message: String) : Exception(message)

public trait ComponentContainer {
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

public class StorageComponentContainer(id: String) : ComponentContainer, Closeable {
    public val unknownContext: ComponentResolveContext by Delegates.lazy { ComponentResolveContext(this, DynamicComponentDescriptor) }
    val componentStorage = ComponentStorage(id)

    override fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext {
        if (requestingDescriptor == DynamicComponentDescriptor) // cache unknown component descriptor
            return unknownContext
        return ComponentResolveContext(this, requestingDescriptor)
    }

    fun compose(): StorageComponentContainer {
        componentStorage.compose()
        return this
    }

    override fun close() = componentStorage.dispose()

    public fun resolve(request: Class<*>, context: ValueResolveContext = unknownContext): ValueDescriptor? {
        return componentStorage.resolve(request, context)
    }

    public fun resolveMultiple(request: Class<*>, context: ValueResolveContext = unknownContext): Iterable<ValueDescriptor> {
        return componentStorage.resolveMultiple(request, context)
    }

    public fun registerDescriptors(descriptors: List<ComponentDescriptor>): StorageComponentContainer {
        componentStorage.registerDescriptors(descriptors)
        return this
    }

}

public fun StorageComponentContainer.register(klass: Class<*>, lifetime: ComponentLifetime = ComponentLifetime.Singleton): StorageComponentContainer =
        when (lifetime) {
            ComponentLifetime.Singleton -> registerSingleton(klass)
            ComponentLifetime.Transient -> registerTransient(klass)
        }


public fun StorageComponentContainer.registerSingleton(klass: Class<*>): StorageComponentContainer {
    return registerDescriptors(listOf(SingletonTypeComponentDescriptor(this, klass)))
}

public fun StorageComponentContainer.registerTransient(klass: Class<*>): StorageComponentContainer {
    return registerDescriptors(listOf(TransientTypeComponentDescriptor(this, klass)))
}

public fun StorageComponentContainer.registerInstance(instance: Any): StorageComponentContainer {
    return registerDescriptors(listOf(ObjectComponentDescriptor(instance)))
}

public inline fun <reified T> StorageComponentContainer.register(lifetime: ComponentLifetime = ComponentLifetime.Singleton): StorageComponentContainer =
        if (lifetime == ComponentLifetime.Singleton) registerSingleton<T>()
        else if (lifetime == ComponentLifetime.Transient) registerTransient<T>()
        else throw IllegalStateException("Unknown lifetime: ${lifetime}}")

public inline fun <reified T> StorageComponentContainer.registerSingleton(): StorageComponentContainer {
    return registerDescriptors(listOf(SingletonTypeComponentDescriptor(this, javaClass<T>())))
}

public inline fun <reified T>  StorageComponentContainer.registerTransient(): StorageComponentContainer {
    return registerDescriptors(listOf(TransientTypeComponentDescriptor(this, javaClass<T>())))
}

public inline fun <reified T> StorageComponentContainer.resolve(context: ValueResolveContext = unknownContext): ValueDescriptor? {
    return resolve(javaClass<T>(), context)
}

public inline fun <reified T> StorageComponentContainer.resolveMultiple(context: ValueResolveContext = unknownContext): Iterable<ValueDescriptor> {
    return resolveMultiple(javaClass<T>(), context)
}
