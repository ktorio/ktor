package org.jetbrains.ktor.components

import java.io.*
import java.lang.reflect.*
import java.util.*

public enum class ComponentStorageState {
    Initial,
    Initialized,
    Disposing,
    Disposed
}

public enum class ComponentInstantiation {
    WithEnvironment,
    OnDemand
}

public enum class ComponentLifetime {
    Singleton,
    Transient
}

public class ComponentStorage(val myId: String) : ValueResolver {
    var state = ComponentStorageState.Initial
    val registry = ComponentRegistry()
    val descriptors = LinkedHashSet<ComponentDescriptor>()
    val dependencies = Multimap<ComponentDescriptor, Type>()

    override fun resolve(request: Type, context: ValueResolveContext): ValueDescriptor? {
        if (state == ComponentStorageState.Initial)
            throw ContainerConsistencyException("Container was not composed before resolving")

        val entry = registry.tryGetEntry(request)
        if (entry != null) {
            registerDependency(request, context)

            val descriptor = entry.singleOrNull()
            return descriptor // we have single component or null (none or multiple)
        }
        return null
    }

    private fun registerDependency(request: Type, context: ValueResolveContext) {
        if (context is ComponentResolveContext) {
            val descriptor = context.requestingDescriptor
            if (descriptor is ComponentDescriptor) {
                dependencies.put(descriptor, request);
            }
        }
    }

    public fun resolveMultiple(request: Type, context: ValueResolveContext): Iterable<ValueDescriptor> {
        registerDependency(request, context)
        return registry.tryGetEntry(request) ?: listOf()
    }

    public fun registerDescriptors(context: ComponentResolveContext, items: List<ComponentDescriptor>) {
        if (state == ComponentStorageState.Disposed) {
            throw ContainerConsistencyException("Cannot register descriptors in $state state")
        }

        descriptors.addAll(items)

        if (state == ComponentStorageState.Initialized)
            composeDescriptors(context, items);
    }

    public fun compose(context: ComponentResolveContext) {
        if (state != ComponentStorageState.Initial)
            throw ContainerConsistencyException("Container $myId was already composed.");

        state = ComponentStorageState.Initialized;
        composeDescriptors(context, descriptors);
    }

    private fun composeDescriptors(context: ComponentResolveContext, descriptors: Collection<ComponentDescriptor>) {
        if (descriptors.isEmpty()) return

        registry.addAll(descriptors)

        // inspect dependencies and register implicit
        val implicitComponents = LinkedHashSet<ComponentDescriptor>()
        val visitedTypes = hashSetOf<Class<*>>()
        for (descriptor in descriptors) {
            discoverImplicitComponents(context, descriptor, implicitComponents, visitedTypes)
        }
        registry.addAll(implicitComponents)

        // instantiate and inject properties
        (descriptors + implicitComponents).forEach { injectMethods(it.getValue(), context) }
    }

    private fun discoverImplicitComponents(context: ComponentResolveContext,
                                           descriptor: ComponentDescriptor,
                                           implicitComponents: MutableSet<ComponentDescriptor>,
                                           visitedClasses: HashSet<Class<*>>) {
        val dependencies = descriptor.getDependencies(context)
        for (type in dependencies) {
            if (type !is Class<*> || !visitedClasses.add(type))
                continue
            visitedClasses.add(type)
            val entry = registry.tryGetEntry(type)
            if (entry == null) {
                val modifiers = type.modifiers
                if (!Modifier.isInterface(modifiers) && !Modifier.isAbstract(modifiers) && !type.isPrimitive) {
                    val implicitDescriptor = SingletonDescriptor(context.container, type)
                    implicitComponents.add(implicitDescriptor)
                    discoverImplicitComponents(context, implicitDescriptor, implicitComponents, visitedClasses)
                }
            }
        }
    }

    private fun injectMethods(instance: Any, context: ValueResolveContext) {
        val type = instance.javaClass
        val injectors = type.methods.filter { member ->
            member.declaredAnnotations.any { it.annotationType().simpleName == "Inject" }
        }

        injectors.forEach { injector ->
            val methodBinding = injector.bindToMethod(context)
            methodBinding.invoke(instance)
        }
    }

    public fun dispose() {
        if (state != ComponentStorageState.Initialized) {
            if (state == ComponentStorageState.Initial)
                return; // it is valid to dispose container which was not initialized
            throw ContainerConsistencyException("Component container cannot be disposed in the $state state.");
        }

        state = ComponentStorageState.Disposing;
        val disposeList = getDescriptorsInDisposeOrder()
        for (descriptor in disposeList)
            disposeDescriptor(descriptor);
        state = ComponentStorageState.Disposed;
    }

    fun getDescriptorsInDisposeOrder(): List<ComponentDescriptor> = topologicalSort(descriptors)
    {
        val dependent = ArrayList<ComponentDescriptor>();
        for (interfaceType in dependencies[it]) {
            val entry = registry.tryGetEntry(interfaceType) ?: continue
            for (dependency in entry) {
                dependent.add(dependency)
            }
        }
        dependent
    }

    fun disposeDescriptor(descriptor: ComponentDescriptor) {
        if (descriptor is Closeable)
            descriptor.close()
    }
}