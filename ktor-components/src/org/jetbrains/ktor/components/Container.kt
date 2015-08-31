package org.jetbrains.ktor.components

import java.io.*
import java.lang.reflect.*

public class ContainerConsistencyException(message: String) : Exception(message)

public interface ComponentContainer {
    fun createResolveContext(requestingDescriptor: ValueDescriptor): ValueResolveContext
}

object DynamicComponentDescriptor : ComponentDescriptor {
    override fun getDependencies(context: ValueResolveContext): Collection<Type> = throw UnsupportedOperationException()
    override fun getRegistrations(): Iterable<Type> = throw UnsupportedOperationException()
    override fun getValue(): Any = throw UnsupportedOperationException()
}

public class StorageComponentContainer(id: String) : ComponentContainer, Closeable {
    public val unknownContext: ComponentResolveContext by lazy { ComponentResolveContext(this, DynamicComponentDescriptor) }

    private val componentStorage = ComponentStorage(id)

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
            val typeArguments = request.actualTypeArguments
            val rawType = request.rawType
            if (rawType == javaClass<Iterable<*>>()) {
                if (typeArguments.size() == 1) {
                    val iterableTypeArgument = typeArguments[0]
                    if (iterableTypeArgument is WildcardType) {
                        val upperBounds = iterableTypeArgument.upperBounds
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
            else -> throw IllegalArgumentException("Unknown lifetime: $lifetime}")
        }


public fun StorageComponentContainer.registerSingleton(klass: Class<*>): StorageComponentContainer {
    return registerDescriptors(listOf(SingletonDescriptor(this, klass)))
}

public fun StorageComponentContainer.registerTransient(klass: Class<*>): StorageComponentContainer {
    return registerDescriptors(listOf(TransientDescriptor(this, klass)))
}

public fun StorageComponentContainer.registerInstance(instance: Any): StorageComponentContainer {
    return registerDescriptors(listOf(InstanceDescriptor(instance)))
}

public inline fun <reified T : Any> StorageComponentContainer.register(lifetime: ComponentLifetime = ComponentLifetime.Singleton): StorageComponentContainer =
        if (lifetime == ComponentLifetime.Singleton) registerSingleton<T>()
        else if (lifetime == ComponentLifetime.Transient) registerTransient<T>()
        else throw IllegalArgumentException("Unknown lifetime: $lifetime}")

public inline fun <reified T : Any> StorageComponentContainer.registerSingleton(): StorageComponentContainer {
    return registerDescriptors(listOf(SingletonDescriptor(this, javaClass<T>())))
}

public inline fun <reified T : Any>  StorageComponentContainer.registerTransient(): StorageComponentContainer {
    return registerDescriptors(listOf(TransientDescriptor(this, javaClass<T>())))
}

public inline fun <reified T : Any> StorageComponentContainer.resolve(context: ValueResolveContext = unknownContext): ValueDescriptor? {
    return resolve(javaClass<T>(), context)
}

public inline fun <reified T : Any> StorageComponentContainer.tryGetComponent(context: ValueResolveContext = unknownContext): T? {
    return resolve(javaClass<T>(), context)?.getValue() as? T
}

public inline fun <reified T : Any> StorageComponentContainer.getComponent(context: ValueResolveContext = unknownContext): T {
    val klass = javaClass<T>()
    val descriptor = resolve(klass, context) ?: throw ContainerConsistencyException("Required component $klass cannot be resolved")
    return descriptor.getValue() as T
}

public inline fun <reified T : Any> StorageComponentContainer.resolveMultiple(context: ValueResolveContext = unknownContext): Iterable<ValueDescriptor> {
    return resolveMultiple(javaClass<T>(), context)
}

fun ComponentContainer.createInstance(klass: Class<*>): Any {
    val context = createResolveContext(DynamicComponentDescriptor)
    return klass.bindToConstructor(context).createInstance()
}

inline fun <reified T : Any> ComponentContainer.createInstance(): T {
    val context = createResolveContext(DynamicComponentDescriptor)
    return javaClass<T>().bindToConstructor(context).createInstance() as T
}

