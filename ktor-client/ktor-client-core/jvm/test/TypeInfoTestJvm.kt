import io.ktor.client.call.*
import kotlin.test.*

class TypeInfoTestJvm {

    @Test
    fun equalsTest() {
        class Foo<Bar>

        assertNotEquals(typeInfo<Foo<String>>(), typeInfo<Foo<Int>>())
        assertNotEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Char>>())
    }
}
