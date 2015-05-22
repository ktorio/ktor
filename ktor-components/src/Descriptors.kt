package ktor.application


public interface ValueDescriptor
{
    fun getValue(): Any
}

public interface ComponentDescriptor : ValueDescriptor
{
    fun getRegistrations(): Iterable<Class<*>>
}

public class ObjectComponentDescriptor(val instance: Any) : ComponentDescriptor {
    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Class<*>> = instance.javaClass.getInterfaces().toList()
}
