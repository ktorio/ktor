package org.jetbrains.container

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.ArrayList

public interface ValueResolver {
    fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor?
}

public interface ValueResolveContext {
    fun resolve(registration: Class<*>): ValueDescriptor?
}

internal class ComponentResolveContext(val container: StorageComponentContainer, val requestingDescriptor: ValueDescriptor) : ValueResolveContext {
    override fun resolve(registration: Class<*>): ValueDescriptor? = container.resolve(registration, this)
    public override fun toString(): String = "for $requestingDescriptor in $container"
}

fun ComponentContainer.createInstance(klass: Class<*>): Any {
    val context = createResolveContext(DynamicComponentDescriptor)
    return klass.bindToConstructor(context).createInstance()
}

public class ConstructorBinding(val constructor: Constructor<*>, val argumentDescriptors: List<ValueDescriptor>) {
    fun createInstance(): Any = constructor.createInstance(argumentDescriptors)
}

public class MethodBinding(val method: Method, val argumentDescriptors: List<ValueDescriptor>) {
    fun invoke(instance: Any) {
        val arguments = bindArguments(argumentDescriptors).toTypedArray()
        method.invoke(instance, *arguments)
    }
}

fun Constructor<*>.createInstance(argumentDescriptors: List<ValueDescriptor>) = newInstance(bindArguments(argumentDescriptors))!!

public fun bindArguments(argumentDescriptors: List<ValueDescriptor>): List<Any> = argumentDescriptors.map { it.getValue() }

fun Class<*>.bindToConstructor(context: ValueResolveContext): ConstructorBinding {
    val candidate = getConstructors().single()
    val parameters = candidate.getParameterTypes()!!
    val arguments = ArrayList<ValueDescriptor>(parameters.size())
    var unsatisfied: MutableList<Type>? = null

    for (parameter in parameters) {
        val descriptor = context.resolve(parameter)
        if (descriptor == null) {
            if (unsatisfied == null)
                unsatisfied = ArrayList<Type>()
            unsatisfied.add(parameter)
        }
        else {
            arguments.add(descriptor)
        }
    }

    if (unsatisfied == null) // constructor is satisfied with arguments
        return ConstructorBinding(candidate, arguments)

    throw UnresolvedDependenciesException("Dependencies for type `$this` cannot be satisfied:\n  ${unsatisfied}")
}

fun Method.bindToMethod(context: ValueResolveContext): MethodBinding {
    val parameters = getParameterTypes()!!
    val arguments = ArrayList<ValueDescriptor>(parameters.size())
    var unsatisfied: MutableList<Type>? = null

    for (parameter in parameters) {
        val descriptor = context.resolve(parameter)
        if (descriptor == null) {
            if (unsatisfied == null)
                unsatisfied = ArrayList<Type>()
            unsatisfied.add(parameter)
        }
        else {
            arguments.add(descriptor)
        }
    }

    if (unsatisfied == null) // constructor is satisfied with arguments
        return MethodBinding(this, arguments)

    throw UnresolvedDependenciesException("Dependencies for method `$this` cannot be satisfied:\n  ${unsatisfied}")
}

class UnresolvedDependenciesException(message: String) : Exception(message)

