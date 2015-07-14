package org.jetbrains.ktor.components

import java.io.*
import java.lang.reflect.*
import kotlin.properties.*

class ContainerConsistencyException(message: String) : Exception(message)

public interface ComponentContainer {
    fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext
}

object DynamicComponentDescriptor : ComponentDescriptor {
    override fun getDependencies(context: ValueResolveContext): Collection<Type> = throw UnsupportedOperationException()
    override fun getRegistrations(): Iterable<Type> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

object UnidentifiedComponentDescriptor : ComponentDescriptor {
    override fun getDependencies(context: ValueResolveContext): Collection<Type> = throw UnsupportedOperationException()
    override fun getRegistrations(): Iterable<Class<*>> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

public class StorageComponentContainer(id: String) : ComponentContainer, Closeable {
    public val unknownContext: ComponentResolveContext by lazy { ComponentResolveContext(this, DynamicComponentDescriptor) }
    val componentStorage = ComponentStorage(id)

    override fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext {
        if (requestingDescriptor == DynamicComponentDescriptor) // cache unknown component descriptor
            return unknownContext
        return ComponentResolveContext(this, requestingDescriptor)
    }

    fun compose(): StorageComponentContainer {
        componentStorage.compose(unknownContext)
        return this
    }

    override fun close() = componentStorage.dispose()

    jvmOverloads public fun resolve(request: Type, context: ValueResolveContext = unknownContext): ValueDescriptor? {
        val storageResolve = componentStorage.resolve(request, context)
        if (storageResolve != null)
            return storageResolve

        if (request is ParameterizedType) {
            val typeArguments = request.getActualTypeArguments()
            val rawType = request.getRawType()
            if (rawType == javaClass<Iterable<*>>()) {
                if (typeArguments.size() == 1) {
                    val iterableTypeArgument = typeArguments[0]
                    if (iterableTypeArgument is WildcardType) {
                        val upperBounds = iterableTypeArgument.getUpperBounds()
                        if (upperBounds.size() == 1) {
                            val iterableType = upperBounds[0]
                            return IterableDescriptor(componentStorage.resolveMultiple(iterableType, context))

                        }
                    }
                }
            }
        }
        return null
    }

    public fun resolveMultiple(request: Class<*>, context: ValueResolveContext = unknownContext): Iterable<ValueDescriptor> {
        return componentStorage.resolveMultiple(request, context)
    }

    public fun registerDescriptors(descriptors: List<ComponentDescriptor>): StorageComponentContainer {
        componentStorage.registerDescriptors(unknownContext, descriptors)
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
    return registerDescriptors(listOf(InstanceComponentDescriptor(instance)))
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
