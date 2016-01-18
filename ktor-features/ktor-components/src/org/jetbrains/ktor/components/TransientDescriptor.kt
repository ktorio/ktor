package org.jetbrains.ktor.components

import java.lang.reflect.*

public class TransientDescriptor(val container: ComponentContainer, val klass: Class<*>) : ComponentDescriptor {
    public override fun getValue(): Any = createInstance(container.createResolveContext(this));
    public override fun getRegistrations(): Iterable<Class<*>> = (klass.interfaces + klass).toList()
    public override fun getDependencies(context: ValueResolveContext): Collection<Type> = calculateClassDependencies(klass)

    protected fun createInstance(context: ValueResolveContext): Any {
        val binding = klass.bindToConstructor(context)
        val constructor = binding.constructor
        val arguments = bindArguments(binding.argumentDescriptors)
        val instance = constructor.newInstance(*arguments.toTypedArray())!!
        return instance
    }

}