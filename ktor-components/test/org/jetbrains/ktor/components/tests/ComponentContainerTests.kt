package org.jetbrains.ktor.components.tests

import org.jetbrains.ktor.components.*
import org.junit.*
import kotlin.test.*

class ComponentContainerTests {
    Test fun `should throw when not composed`() {
        val container = StorageComponentContainer("test")
        fails { container.resolve<TestComponentInterface>() }
    }

    Test fun should_resolve_to_null_when_empty() {
        val container = StorageComponentContainer("test").compose()
        assertNull(container.resolve<TestComponentInterface>())
    }

    Test fun should_resolve_to_instance_when_registered() {
        val container = StorageComponentContainer("test").register<TestComponent>().compose()
        val descriptor = container.resolve<TestComponentInterface>()
        assertNotNull(descriptor)
        val instance = descriptor!!.getValue() as TestComponentInterface
        assertNotNull(instance)
        fails {
            instance.foo()
        }
    }

    Test fun should_resolve_instance_dependency() {
        val container = StorageComponentContainer("test")
                .registerInstance(ManualTestComponent("name"))
                .register<TestClientComponent>()
                .compose()
        val descriptor = container.resolve<TestClientComponent>()
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
        val container = StorageComponentContainer("test")
                .register<TestComponent>()
                .register<TestClientComponent>()
                .compose()

        val descriptor = container.resolve<TestClientComponent>()
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
        StorageComponentContainer("test")
                .register<TestComponent>()
                .register<TestClientComponent>()
                .register<TestClientComponent2>()
                .compose()
                .use {
                    val descriptor = it.resolveMultiple<TestClientComponentInterface>()
                    assertNotNull(descriptor)
                    assertEquals(2, descriptor.count())
                }
    }

    Test fun should_resolve_transient_types_to_different_instances() {
        StorageComponentContainer("test")
                .register<TestComponent>()
                .register<TestClientComponent>(ComponentLifetime.Transient)
                .compose()
                .use {
                    val descriptor1 = it.resolve<TestClientComponentInterface>()
                    assertNotNull(descriptor1)
                    val descriptor2 = it.resolve<TestClientComponentInterface>()
                    assertNotNull(descriptor2)
                    assertTrue(descriptor1 == descriptor2)
                    assertFalse(descriptor1!!.getValue() == descriptor2!!.getValue())
                }
    }

    Test fun should_resolve_singleton_types_to_same_instances() {
        StorageComponentContainer("test")
                .register<TestComponent>()
                .register<TestClientComponent>(ComponentLifetime.Singleton)
                .compose()
                .use {
                    val descriptor1 = it.resolve<TestClientComponentInterface>()
                    assertNotNull(descriptor1)
                    val descriptor2 = it.resolve<TestClientComponentInterface>()
                    assertNotNull(descriptor2)
                    assertTrue(descriptor1 == descriptor2)
                    assertTrue(descriptor1!!.getValue() == descriptor2!!.getValue())
                }
    }

    Test fun should_resolve_adhoc_types_to_same_instances() {
        StorageComponentContainer("test")
                .register<TestAdhocComponent1>()
                .register<TestAdhocComponent2>()
                .compose()
                .use {
                    val descriptor1 = it.resolve<TestAdhocComponent1>()
                    assertNotNull(descriptor1)
                    val descriptor2 = it.resolve<TestAdhocComponent2>()
                    assertNotNull(descriptor2)
                    val component1 = descriptor1!!.getValue() as TestAdhocComponent1
                    val component2 = descriptor2!!.getValue() as TestAdhocComponent2
                    assertTrue(component1.service === component2.service)
                }
    }

    Test fun should_resolve_iterable() {
        StorageComponentContainer("test")
                .register<TestComponent>()
                .register<TestClientComponent>()
                .register<TestClientComponent2>()
                .register<TestIterableComponent>()
                .compose()
                .use {
                    val descriptor = it.resolve<TestIterableComponent>()
                    assertNotNull(descriptor)
                    val iterableComponent = descriptor!!.getValue() as TestIterableComponent
                    assertEquals(2, iterableComponent.components.count())
                    assertTrue(iterableComponent.components.any { it is TestClientComponent })
                    assertTrue(iterableComponent.components.any { it is TestClientComponent2 })
                }
    }

    Test fun should_distinguish_generic() {
        StorageComponentContainer("test")
                .register<TestGenericClient>()
                .register<TestStringComponent>()
                .register<TestIntComponent>()
                .compose()
                .use {
                    val descriptor = it.resolve<TestGenericClient>()
                    assertNotNull(descriptor)
                    val genericClient = descriptor!!.getValue() as TestGenericClient
                    assertTrue(genericClient.component1 is TestStringComponent)
                    assertTrue(genericClient.component2 is TestIntComponent)
                }
    }

}