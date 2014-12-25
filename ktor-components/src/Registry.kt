package ktor.application

import java.util.*
import ktor.datastructures.Multimap

public fun ComponentRegisterEntry(value: ComponentRegisterEntry): ComponentRegisterEntry {
    val entry = ComponentRegisterEntry()
    entry.descriptors.addAll(value.descriptors)
    return entry
}


class ComponentRegisterEntry() : Iterable<ComponentDescriptor> {
    val descriptors: MutableList<ComponentDescriptor> = ArrayList()

    override fun iterator(): Iterator<ComponentDescriptor> = descriptors.iterator()

    public fun singleOrDefault(): ComponentDescriptor? {
        if (descriptors.size() == 1)
            return descriptors[0]
        else
            return null
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
    fun buildRegistrationMap(descriptors: Collection<ComponentDescriptor>): Multimap<Class<*>, ComponentDescriptor> {
        val registrationMap = Multimap<Class<*>, ComponentDescriptor>()
        for (descriptor in descriptors)
            for (registration in descriptor.getRegistrations())
                registrationMap.put(registration, descriptor)
        return registrationMap
    }

    private var registrationMap = HashMap<Any, ComponentRegisterEntry>(8)

    public fun addAll(descriptors: Collection<ComponentDescriptor>) {
        val updateMap = buildRegistrationMap(descriptors)
        val lastMap = registrationMap
        val newMap = HashMap<Any, ComponentRegisterEntry>(lastMap.size())
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
        val interfaceMap = HashMap<Any, ComponentRegisterEntry>(lastMap.size())
        for ((key, value) in lastMap)
            interfaceMap.put(key, ComponentRegisterEntry(value))

        for (key in newMap.keys()) {
            val entry = interfaceMap.getOrPut(key, { ComponentRegisterEntry() })
            entry.removeAll(newMap[key]!!)
        }
        registrationMap = interfaceMap
    }

    public fun tryGetEntry(request: Class<*>): ComponentRegisterEntry? {
        return registrationMap.getOrElse(request, { null })
    }
}