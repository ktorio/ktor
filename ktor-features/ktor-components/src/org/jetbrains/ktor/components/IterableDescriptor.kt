package org.jetbrains.ktor.components

public class IterableDescriptor(val descriptors: Iterable<ValueDescriptor>) : ValueDescriptor {
    override fun getValue(): Any = descriptors.map { it.getValue() }
}