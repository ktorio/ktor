package org.jetbrains.ktor.components

import java.lang.reflect.*
import java.util.*

public interface ValueDescriptor {
    public fun getValue(): Any
}

public interface ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Type>
    fun getDependencies(context: ValueResolveContext): Collection<Type>
}

private fun collectInterfacesRecursive(type: Type, result: MutableSet<Type>) {
    val cl : Class<*>? = when(type) {
        is Class<*> -> type
        is ParameterizedType -> type.getRawType() as? Class<*>
        else -> null
    }

    val interfaces = cl?.getGenericInterfaces()
    interfaces?.forEach {
        if (result.add(it) && it is Class<*>) {
            collectInterfacesRecursive(it, result)
        }
    }
}

private fun getRegistrationsForClass(klass: Class<*>): List<Type> {
    val registrations = ArrayList<Type>()
    val superClasses = sequence<Type>(klass) {
        when (it) {
            is Class<*> -> it.getGenericSuperclass()
            is ParameterizedType -> it.getRawType() as? Class<*>
            else -> null
        }
        // todo: do not publish as Object
    }
    registrations.addAll(superClasses)
    val interfaces = LinkedHashSet<Type>()
    superClasses.forEach { collectInterfacesRecursive(it, interfaces) }
    registrations.addAll(interfaces)
    return registrations
}

public class IterableDescriptor(val descriptors: Iterable<ValueDescriptor>) : ValueDescriptor {
    override fun getValue(): Any {
        return descriptors.map { it.getValue() }
    }
}