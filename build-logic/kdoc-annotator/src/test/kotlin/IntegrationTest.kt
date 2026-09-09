import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationTest {
    @Test
    fun testLockFreeLinkedList() {

        val projectSources = "./src/test/test-data"
        val link = "https://ktor.io/feedback"


        val path = Path("../")

        forEachKtFileInDirectory(Path(projectSources)) { ktFile, path ->
            annotatePublicApiKDocs(ktFile, path, link)
        }
        
    }

    @Test
    fun `detect test source sets`() {
        assertTrue(Path("project/src/test/kotlin/Example.kt").isInTestSourceSet())
        assertTrue(Path("project/src/commonTest/kotlin/Example.kt").isInTestSourceSet())
        assertTrue(Path("project/src/integrationTest/kotlin/Example.kt").isInTestSourceSet())
        assertTrue(Path("project/jvm/test/io/ktor/RequestHeadersTest.kt").isInTestSourceSet())
        assertFalse(Path("project/src/main/kotlin/RequestHeadersTest.kt").isInTestSourceSet())
        assertFalse(Path("project/src/commonMain/kotlin/Example.kt").isInTestSourceSet())
        assertFalse(Path("project/ktor-client-tests/jvm/main/Example.kt").isInTestSourceSet())
    }
}