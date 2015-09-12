package org.jetbrains.ktor.components

import java.lang.reflect.*
import java.util.*

private fun collectInterfacesRecursive(type: Type, result: MutableSet<Type>) {
    val cl: Class<*>? = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
        else -> null
    }

    val interfaces = cl?.genericInterfaces
    interfaces?.forEach {
        if (result.add(it) && it is Class<*>) {
            collectInterfacesRecursive(it, result)
        }
    }
}

internal fun calculateClassRegistrations(klass: Class<*>): List<Type> {
    val registrations = ArrayList<Type>()
    val superClasses = sequence<Type>(klass) {
        when (it) {
            is Class<*> -> it.genericSuperclass
            is ParameterizedType -> it.rawType as? Class<*>
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

internal fun calculateClassDependencies(klass: Class<*>): Set<Type> {
    val dependencies = hashSetOf<Type>()
    dependencies.addAll(klass.constructors.single().genericParameterTypes)

    for (member in klass.methods) {
        val annotations = member.declaredAnnotations
        for (annotation in annotations) {
            val annotationType = annotation.annotationType()
            if (annotationType.name.substringAfterLast('.') == "Inject") {
                dependencies.addAll(member.genericParameterTypes)
            }
        }
    }

    return dependencies
}

