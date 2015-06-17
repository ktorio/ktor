package org.jetbrains.ktor.components

import java.lang.reflect.*
import java.util.*

public interface ValueDescriptor {
    public fun getValue(): Any
}

public interface ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Class<*>>
    fun getDependencies(context: ValueResolveContext): Collection<Class<*>>
}

private fun collectInterfacesRecursive(cl: Class<*>, result: MutableSet<Class<*>>) {
    val interfaces = cl.getInterfaces()
    interfaces.forEach {
        if (result.add(it)) {
            collectInterfacesRecursive(it, result)
        }
    }
}

private fun getRegistrationsForClass(klass: Class<*>): List<Class<*>> {
    val registrations = ArrayList<Class<*>>()
    val superClasses = sequence(klass) {
        val superclass = it.getGenericSuperclass()
        when (superclass) {
            is ParameterizedType -> superclass.getRawType() as? Class<*>
            is Class<*> -> superclass
            else -> null
        }
        // todo: do not publish as Object
    }
    registrations.addAll(superClasses)
    val interfaces = LinkedHashSet<Class<*>>()
    superClasses.forEach { collectInterfacesRecursive(it, interfaces) }
    registrations.addAll(interfaces)
    return registrations
}