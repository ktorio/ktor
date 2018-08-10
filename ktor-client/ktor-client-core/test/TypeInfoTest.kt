import io.ktor.client.call.*
import kotlin.test.*


class TypeInfoTest {

    @Test
    fun classInMethodTest() {
        class Foo
        typeInfo<Foo>()
    }

    @Test
    @Ignore
    fun typeInfoWithClassDefinedInMethodScopeWithComplexName() {
        class SomeClass
        typeInfo<SomeClass>()
    }

    @Test
    fun equalsTest() {
        class Foo<Bar>
        assertEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Int>>())
    }
}
