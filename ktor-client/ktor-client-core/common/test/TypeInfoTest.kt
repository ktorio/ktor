import io.ktor.client.call.*
import kotlin.test.*


class TypeInfoTest {

    @Test
    fun testClassInMethod() {
        class Foo
        typeInfo<Foo>()
    }

    @Test
    @Ignore
    fun testTypeInfoWithClassDefinedInMethodScopeWithComplexName() {
        class SomeClass
        typeInfo<SomeClass>()
    }

    @Test
    fun testEquals() {
        class Foo<Bar>
        assertEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Int>>())
    }
}
