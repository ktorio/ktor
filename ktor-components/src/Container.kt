package ktor.application

import kotlin.properties.Delegates
import java.io.Closeable

class ContainerConsistencyException(message: String) : Exception(message)

public trait IComponentContainer
{
    fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext
}

object DynamicComponentDescriptor : ComponentDescriptor {
    override fun getRegistrations(): Iterable<Class<out Any?>> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

object UnidentifiedComponentDescriptor : ComponentDescriptor {
    override fun getRegistrations(): Iterable<Class<out Any?>> = throw UnsupportedOperationException()
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

    fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor?
    {
        return componentStorage.resolve(request, context)
    }

    fun resolveMultiple(request: Class<*>, context: ValueResolveContext): Iterable<ValueDescriptor>
    {
        return componentStorage.resolveMultiple(request, context)
    }

    public fun registerDescriptors(descriptors: List<ComponentDescriptor>): ComponentContainer
    {
        componentStorage.registerDescriptors(descriptors)
        return this
    }

}

public fun ComponentContainer.register(klass: Class<*>, lifetime: ComponentLifetime = ComponentLifetime.Singleton): ComponentContainer =
        when (lifetime) {
            ComponentLifetime.Singleton -> registerSingleton(klass)
            ComponentLifetime.Transient -> registerTransient(klass)
        }

public fun ComponentContainer.registerSingleton(klass: Class<*>): ComponentContainer = registerDescriptors(listOf(SingletonTypeComponentDescriptor(this, klass)))
public fun ComponentContainer.registerTransient(klass: Class<*>): ComponentContainer = registerDescriptors(listOf(TransientTypeComponentDescriptor(this, klass)))
public fun ComponentContainer.registerInstance(instance: Any): ComponentContainer = registerDescriptors(listOf(ObjectComponentDescriptor(instance)))

