package org.jetbrains.ktor.components.tests

import java.io.*

interface TestComponentInterface {
    public val disposed: Boolean
    fun foo()
}

interface TestClientComponentInterface {
}

class TestComponent : TestComponentInterface, Closeable {
    public override var disposed: Boolean = false
    override fun close() {
        disposed = true
    }

    override fun foo() {
        throw UnsupportedOperationException()
    }
}

class ManualTestComponent(val name: String) : TestComponentInterface, Closeable {
    public override var disposed: Boolean = false
    override fun close() {
        disposed = true
    }

    override fun foo() {
        throw UnsupportedOperationException()
    }
}

class TestClientComponent(val dep: TestComponentInterface) : TestClientComponentInterface, Closeable {
    override fun close() {
        if (dep.disposed)
            throw Exception("Dependency shouldn't be disposed before dependee")
        disposed = true
    }

    var disposed: Boolean = false
}

class TestClientComponent2() : TestClientComponentInterface {
}
