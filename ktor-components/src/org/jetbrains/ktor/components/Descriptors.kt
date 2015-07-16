package org.jetbrains.ktor.components

import java.lang.reflect.*
import java.util.*

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

private fun calculateClassRegistrations(klass: Class<*>): List<Type> {
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

public fun calculateClassDependencies(klass: Class<*>): ArrayList<Type> {
    val dependencies = ArrayList<Type>()
    dependencies.addAll(klass.getConstructors().single().getGenericParameterTypes())

    for (member in klass.getMethods()) {
        val annotations = member.getDeclaredAnnotations()
        for (annotation in annotations) {
            val annotationType = annotation.annotationType()
            if (annotationType.getName().substringAfterLast('.') == "Inject") {
                dependencies.addAll(member.getGenericParameterTypes())
            }
        }
    }

    return dependencies
}

