package ktor.tests.application

import ktor.application.*
import org.junit.*
import kotlin.test.*
import java.io.Closeable

trait TestComponentInterface {
    public val disposed: Boolean
    fun foo()
}

trait TestClientComponentInterface {
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

class ComponentContainerTests {
    Test fun should_throw_when_not_composed() {
        val container = ComponentContainer("test")
        fails {
            container.resolve(javaClass<TestComponentInterface>(), container.unknownContext)
        }
    }

    Test fun should_resolve_to_null_when_empty() {
        val container = ComponentContainer("test").compose()
        assertNull(container.resolve(javaClass<TestComponentInterface>(), container.unknownContext))
    }

    Test fun should_resolve_to_instance_when_registered() {
        val container = ComponentContainer("test").register(javaClass<TestComponent>()).compose()
        val descriptor = container.resolve(javaClass<TestComponentInterface>(), container.unknownContext)
        assertNotNull(descriptor)
        val instance = descriptor!!.getValue() as TestComponentInterface
        assertNotNull(instance)
        fails {
            instance.foo()
        }
    }

    Test fun should_resolve_instance_dependency() {
        val container = ComponentContainer("test")
                .registerInstance(ManualTestComponent("name"))
                .register(javaClass<TestClientComponent>())
                .compose()
        val descriptor = container.resolve(javaClass<TestClientComponent>(), container.unknownContext)
        assertNotNull(descriptor)
        val instance = descriptor!!.getValue() as TestClientComponent
        assertNotNull(instance)
        assertNotNull(instance.dep)
        fails {
            instance.dep.foo()
        }
        assertTrue(instance.dep is ManualTestComponent)
        assertEquals("name", (instance.dep as ManualTestComponent).name)
        container.close()
        assertTrue(instance.disposed)
        assertFalse(instance.dep.disposed) // should not dispose manually passed instances
    }

    Test fun should_resolve_type_dependency() {
        val container = ComponentContainer("test")
                .register(javaClass<TestComponent>())
                .register(javaClass<TestClientComponent>())
                .compose()

        val descriptor = container.resolve(javaClass<TestClientComponent>(), container.unknownContext)
        assertNotNull(descriptor)
        val instance = descriptor!!.getValue() as TestClientComponent
        assertNotNull(instance)
        assertNotNull(instance.dep)
        fails {
            instance.dep.foo()
        }
        container.close()
        assertTrue(instance.disposed)
        assertTrue(instance.dep.disposed)
    }

    Test fun should_resolve_multiple_types() {
        val container = ComponentContainer("test")
                .register(javaClass<TestComponent>())
                .register(javaClass<TestClientComponent>())
                .register(javaClass<TestClientComponent2>())
                .compose()

        container.use {
            val descriptor = container.resolveMultiple(javaClass<TestClientComponentInterface>(), container.unknownContext)
            assertNotNull(descriptor)
            assertEquals(2, descriptor.count())
        }
    }

    Test fun should_resolve_transient_types_to_different_instances() {
        val container = ComponentContainer("test")
                .register(javaClass<TestComponent>())
                .register(javaClass<TestClientComponent>(), ComponentLifetime.Transient)
                .compose()

        container.use {
            val descriptor1 = container.resolve(javaClass<TestClientComponentInterface>(), container.unknownContext)
            assertNotNull(descriptor1)
            val descriptor2 = container.resolve(javaClass<TestClientComponentInterface>(), container.unknownContext)
            assertNotNull(descriptor2)
            assertTrue(descriptor1 == descriptor2)
            assertFalse(descriptor1!!.getValue() == descriptor2!!.getValue())
        }
    }

    Test fun should_resolve_singleton_types_to_same_instances() {
        val container = ComponentContainer("test")
                .register(javaClass<TestComponent>())
                .register(javaClass<TestClientComponent>(), ComponentLifetime.Singleton)
                .compose()

        container.use {
            val descriptor1 = container.resolve(javaClass<TestClientComponentInterface>(), container.unknownContext)
            assertNotNull(descriptor1)
            val descriptor2 = container.resolve(javaClass<TestClientComponentInterface>(), container.unknownContext)
            assertNotNull(descriptor2)
            assertTrue(descriptor1 == descriptor2)
            assertTrue(descriptor1!!.getValue() == descriptor2!!.getValue())
        }
    }

}