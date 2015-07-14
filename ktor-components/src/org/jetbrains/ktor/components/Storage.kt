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
    val composingDescriptors = LinkedHashSet<ComponentDescriptor>()

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

    public fun resolveMultiple(request: Type, context: ValueResolveContext): Iterable<ValueDescriptor> {
        registerDependency(request, context)
        return registry.tryGetEntry(request) ?: listOf()
    }

    public fun registerDescriptors(context: ComponentResolveContext, items: List<ComponentDescriptor>) {
        if (state == ComponentStorageState.Disposed) {
            throw ContainerConsistencyException("Cannot register descriptors in $state state")
        }

        for (descriptor in items)
            descriptors.add(descriptor);

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

        composingDescriptors.addAll(descriptors)
        registry.addAll(descriptors);

        // inspect descriptors and register providers
        // TODO

        // inspect dependencies and register implicit
        val implicits = LinkedHashSet<ComponentDescriptor>()
        val visitedTypes = hashSetOf<Class<*>>()
        for (descriptor in descriptors) {
            registerImplicits(context, descriptor, implicits, visitedTypes)
        }
        registry.addAll(implicits)

        // instantiate and inject properties
        for (value in (descriptors + implicits).map { it.getValue() }) {
            injectProperties(value, context)
        }
    }

    private fun registerImplicits(context: ComponentResolveContext, descriptor: ComponentDescriptor, implicits: MutableSet<ComponentDescriptor>, visitedClasses: HashSet<Class<*>>) {
        val dependencies = descriptor.getDependencies(context)
        for (type in dependencies) {
            if (type !is Class<*> || !visitedClasses.add(type))
                continue
            visitedClasses.add(type)
            val entry = registry.tryGetEntry(type)
            if (entry == null) {
                val modifiers = type.getModifiers()
                if (!Modifier.isInterface(modifiers) && !Modifier.isAbstract(modifiers) && !type.isPrimitive()) {
                    val implicitDescriptor = SingletonTypeComponentDescriptor(context.container, type)
                    implicits.add(implicitDescriptor)
                    registerImplicits(context, implicitDescriptor, implicits, visitedClasses)
                }
            }
        }
    }

    private fun injectProperties(instance: Any, context: ValueResolveContext) {
        val type = instance.javaClass
        val injectors = LinkedHashSet<Method>()
        for (member in type.getMethods()) {
            val annotations = member.getDeclaredAnnotations()
            for (annotation in annotations) {
                val annotationType = annotation.annotationType()
                if (annotationType.getName().substringAfterLast('.') == "Inject") {
                    injectors.add(member)
                }
            }
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