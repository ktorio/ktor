package ktor.application

public abstract class TransientDescriptor(val container: IComponentContainer) : ComponentDescriptor {
    public override fun getValue(): Any = createInstance(container.createResolveContext(this));

    protected abstract fun createInstance(context: ValueResolveContext): Any
}