package ktor.application

public abstract class TransientDescriptor(val container: ComponentContainer) : ComponentDescriptor {
    public override fun getValue(): Any = createInstance(container.createResolveContext(this));

    protected abstract fun createInstance(context: ValueResolveContext): Any
}