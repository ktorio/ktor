package org.jetbrains.ktor.components

import java.lang.reflect.*
import java.util.*

class ComponentRegisterEntry : Iterable<ComponentDescriptor> {
    val descriptors: MutableList<ComponentDescriptor> = ArrayList()

    constructor() {
    }

    constructor(value: ComponentRegisterEntry) {
        descriptors.addAll(value.descriptors)
    }

    override fun iterator(): Iterator<ComponentDescriptor> = descriptors.iterator()

    public fun singleOrNull(): ComponentDescriptor? {
        if (descriptors.size() == 1)
            return descriptors[0]
        else if (descriptors.size() == 0)
            return null

        throw UnresolvedDependenciesException("Invalid arity")
    }

    public fun add(item: ComponentDescriptor) {
        descriptors.add(item)
    }

    public fun addAll(items: Collection<ComponentDescriptor>) {
        descriptors.addAll(items)
    }

    public fun remove(item: ComponentDescriptor) {
        descriptors.remove(item)
    }

    public fun removeAll(items: Collection<ComponentDescriptor>) {
        descriptors.removeAll(items)
    }
}

internal class ComponentRegistry {
    fun buildRegistrationMap(descriptors: Collection<ComponentDescriptor>): Multimap<Type, ComponentDescriptor> {
        val registrationMap = Multimap<Type, ComponentDescriptor>()
        for (descriptor in descriptors)
            for (registration in descriptor.getRegistrations())
                registrationMap.put(registration, descriptor)
        return registrationMap
    }

    private var registrationMap = LinkedHashMap<Any, ComponentRegisterEntry>(8)

    public fun addAll(descriptors: Collection<ComponentDescriptor>) {
        val updateMap = buildRegistrationMap(descriptors)
        val lastMap = registrationMap
        val newMap = LinkedHashMap<Any, ComponentRegisterEntry>(lastMap.size())
        for ((key, value) in lastMap)
            newMap.put(key, ComponentRegisterEntry(value))

        for ((key, value) in updateMap) {
            val entry = newMap.getOrPut(key, { ComponentRegisterEntry() })
            entry.add(value)
        }

        registrationMap = newMap
    }

    public fun removeAll(descriptors: Collection<ComponentDescriptor>) {
        val newMap = buildRegistrationMap(descriptors)
        val lastMap = registrationMap
        val interfaceMap = LinkedHashMap<Any, ComponentRegisterEntry>(lastMap.size())
        for ((key, value) in lastMap)
            interfaceMap.put(key, ComponentRegisterEntry(value))

        for (key in newMap.keys()) {
            val entry = interfaceMap.getOrPut(key, { ComponentRegisterEntry() })
            entry.removeAll(newMap[key])
        }
        registrationMap = interfaceMap
    }

    public fun tryGetEntry(request: Type): ComponentRegisterEntry? = registrationMap[request]
}