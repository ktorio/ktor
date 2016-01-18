package org.jetbrains.ktor.components.tests

import java.io.*

annotation class Inject()

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

class TestAdhocComponentService
class TestAdhocComponent1(val service: TestAdhocComponentService) {

}

class TestAdhocComponent2(val service: TestAdhocComponentService) {

}

class TestIterableComponent(val components: Iterable<TestClientComponentInterface>)

interface TestGenericComponent<T>

class TestGenericClient(val component1 : TestGenericComponent<String>, val component2: TestGenericComponent<Int>)
class TestStringComponent : TestGenericComponent<String>
class TestIntComponent : TestGenericComponent<Int>

class TestInjectMembers {
    var component1 : TestGenericComponent<String>? = null
        get@Inject set

    var component2 : TestGenericComponent<Int>? = null

    @Inject
    fun setComponents(component : TestGenericComponent<Int>) {
        component2 = component
    }
}