package ktor.application

import java.lang.reflect.*
import java.util.ArrayList

public interface ValueResolver
{
    fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor?
}

public interface ValueResolveContext
{
    fun resolve(registration: Class<*>): ValueDescriptor?
}

internal class ComponentResolveContext(val container: StorageComponentContainer, val requestingDescriptor: ValueDescriptor) : ValueResolveContext
{
    override fun resolve(registration: Class<*>): ValueDescriptor? = container.resolve(registration, this)
    public override fun toString(): String = "for $requestingDescriptor in $container"
}

fun ComponentContainer.createInstance(klass: Class<*>): Any {
    val context = createResolveContext(DynamicComponentDescriptor)
    return klass.bindToConstructor(context).createInstance()
}

public class Binding(val constructor: Constructor<*>, val argumentDescriptors: List<ValueDescriptor>) {
    fun createInstance(): Any = constructor.createInstance(argumentDescriptors)
}

fun Constructor<*>.createInstance(argumentDescriptors: List<ValueDescriptor>) = newInstance(bindArguments(argumentDescriptors))!!

public fun bindArguments(argumentDescriptors: List<ValueDescriptor>): List<Any> = argumentDescriptors.map { it.getValue() }

fun Class<*>.bindToConstructor(context: ValueResolveContext): Binding
{
    val candidates = getConstructors()
    val resolved = ArrayList<Binding>()
    val rejected = ArrayList<Pair<Constructor<*>, List<Type>>>()
    for (candidate in candidates)
    {
        val parameters = candidate.getParameterTypes()!!
        val arguments = ArrayList<ValueDescriptor>(parameters.size())
        var unsatisfied: MutableList<Type>? = null

        for (parameter in parameters)
        {
            val descriptor = context.resolve(parameter)
            if (descriptor == null)
            {
                if (unsatisfied == null)
                    unsatisfied = ArrayList<Type>()
                unsatisfied.add(parameter)
            } else {
                arguments.add(descriptor)
            }
        }

        if (unsatisfied == null) // constructor is satisfied with arguments
            resolved.add(Binding(candidate, arguments))
        else
            rejected.add(candidate to unsatisfied)
    }

    if (resolved.size() != 1)
        throw UnresolvedConstructorException()

    return resolved[0]
}

class UnresolvedConstructorException : Exception()

