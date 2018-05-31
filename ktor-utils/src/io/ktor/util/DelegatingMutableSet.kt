package io.ktor.util

internal open class DelegatingMutableSet<From, To>(
    private val delegate: MutableSet<From>,
    private val convertTo: From.() -> To,
    private val convert: To.() -> From
) : MutableSet<To> {

    open fun Collection<To>.convert(): Collection<From> = map { it.convert() }
    open fun Collection<From>.convertTo(): Collection<To> = map { it.convertTo() }

    override val size: Int = delegate.size

    override fun add(element: To): Boolean = delegate.add(element.convert())

    override fun addAll(elements: Collection<To>): Boolean = delegate.addAll(elements.convert())

    override fun clear() {
        delegate.clear()
    }

    override fun remove(element: To): Boolean = delegate.remove(element.convert())

    override fun removeAll(elements: Collection<To>): Boolean = delegate.removeAll(elements.convert())

    override fun retainAll(elements: Collection<To>): Boolean = delegate.retainAll(elements.convert())

    override fun contains(element: To): Boolean = delegate.contains(element.convert())

    override fun containsAll(elements: Collection<To>): Boolean = delegate.containsAll(elements.convert())

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun iterator(): MutableIterator<To> = object : MutableIterator<To> {
        val delegateIterator = delegate.iterator()

        override fun hasNext(): Boolean = delegateIterator.hasNext()

        override fun next(): To = delegateIterator.next().convertTo()

        override fun remove() = delegateIterator.remove()
    }

    override fun hashCode(): Int = delegate.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Set<*>) return false

        return other.containsAll(delegate.convertTo())
    }

    override fun toString(): String = delegate.convertTo().toString()
}