package ktor.application

import java.io.Closeable

abstract class SingletonComponentDescriptor(container: ComponentContainer, val klass: Class<*>) : SingletonDescriptor(container) {
    public override fun getRegistrations(): Iterable<Class<*>> {
        return (klass.getInterfaces() + klass).toList()
    }
}

public class SingletonTypeComponentDescriptor(container: ComponentContainer, klass: Class<*>) : SingletonComponentDescriptor(container, klass) {
    override fun createInstance(context: ValueResolveContext): Any = createInstanceOf(klass, context)
    private fun createInstanceOf(klass: Class<*>, context: ValueResolveContext): Any {
        val binding = klass.bindToConstructor(context)
        state = ComponentState.Initializing
        for (argumentDescriptor in binding.argumentDescriptors) {
            if (argumentDescriptor is Closeable && argumentDescriptor !is SingletonDescriptor) {
                registerDisposableObject(argumentDescriptor)
            }
        }

        val constructor = binding.constructor
        val arguments = bindArguments(binding.argumentDescriptors)

        val instance = constructor.newInstance(*arguments.copyToArray())!!
        state = ComponentState.Initialized
        return instance
    }
}

public class TransientTypeComponentDescriptor(container: ComponentContainer, val klass: Class<*>) : TransientDescriptor(container) {
    protected override fun createInstance(context: ValueResolveContext): Any {
        val binding = klass.bindToConstructor(context)
        val constructor = binding.constructor
        val arguments = bindArguments(binding.argumentDescriptors)
        val instance = constructor.newInstance(*arguments.copyToArray())!!
        return instance
    }

    public override fun getRegistrations(): Iterable<Class<*>> {
        return (klass.getInterfaces() + klass).toList()
    }

}