package ktor.application

import java.util.*
import ktor.datastructures.*
import java.io.Closeable

public enum class ComponentStorageState {
    Initial
    Initialized
    Disposing
    Disposed
}

enum class ComponentInstantiation {
    WithEnvironment
    OnDemand
}

enum class ComponentLifetime {
    Singleton
    Transient
}

public class ComponentStorage(val myId: String) : ValueResolver {
    var state = ComponentStorageState.Initial
    val registry = ComponentRegistry()
    val descriptors = HashSet<ComponentDescriptor>()
    val dependencies = Multimap<ComponentDescriptor, Class<*>>();

    override fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor? {
        if (state == ComponentStorageState.Initial)
            throw ContainerConsistencyException("Container was not composed before resolving")

        val entry = registry.tryGetEntry(request)
        if (entry != null) {
            registerDependency(request, context)

            val descriptor = entry.singleOrDefault()
            return descriptor // we have single component or null (none or multiple)
        }
        return null
    }

    private fun registerDependency(request: Class<*>, context: ValueResolveContext) {
        if (context is ComponentResolveContext) {
            val descriptor = context.requestingDescriptor
            if (descriptor is ComponentDescriptor) {
                /*
                        var requestingDescriptor = componentContext.RequestingDescriptor as IComponentDescriptor;
                        if (requestingDescriptor == null || requestingDescriptor == DynamicComponentDescriptor.Instance || requestingDescriptor == UnidentifiedComponentDescriptor.Instance)
                            return;
                */

                // CheckCircularDependencies(requestingDescriptor, requestingDescriptor, request, Stack<Pair<IComponentDescriptor, Any>>(), HashSet<Any>());
                dependencies.put(descriptor, request);
            }
        }
    }

    public fun resolveMultiple(request: Class<*>, context: ValueResolveContext): Iterable<ValueDescriptor> {
        registerDependency(request, context)
        return registry.tryGetEntry(request) ?: listOf()
    }

    public fun registerDescriptors(items: List<ComponentDescriptor>) {
        if (state == ComponentStorageState.Disposed) {
            throw ContainerConsistencyException("Cannot register descriptors in $state state")
        }

        for (descriptor in items)
            descriptors.add(descriptor);

        if (state == ComponentStorageState.Initialized)
            composeDescriptors(items);

    }

    public fun compose() {
        if (state != ComponentStorageState.Initial)
            throw ContainerConsistencyException("Container $myId was already composed.");

        state = ComponentStorageState.Initialized;
        composeDescriptors(descriptors);
    }

    private fun composeDescriptors(descriptors: Collection<ComponentDescriptor>) {
        registry.addAll(descriptors);
        InstantiateDescriptors(descriptors);
    }

    fun InstantiateDescriptors(descriptors: Collection<ComponentDescriptor>) {
        for (descriptor in descriptors)
            descriptor.getValue();
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

    fun getDescriptorsInDisposeOrder(): List<ComponentDescriptor> {
        return topologicalSort(descriptors)
        {
            val dependent = ArrayList<ComponentDescriptor>();
            for (interfaceType in dependencies[it]) {
                val entry = registry.tryGetEntry(interfaceType)
                if (entry == null)
                    continue;
                for (dependency in entry) {
                    dependent.add(dependency);
                }
            }
            dependent;
        }
    }

    fun disposeDescriptor(descriptor: ComponentDescriptor) {
        if (descriptor is Closeable)
            descriptor.close();
    }
}