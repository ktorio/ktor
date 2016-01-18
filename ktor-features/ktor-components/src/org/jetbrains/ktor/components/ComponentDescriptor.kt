package org.jetbrains.ktor.components

import java.lang.reflect.*

public interface ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Type>
    fun getDependencies(context: ValueResolveContext): Collection<Type>
}