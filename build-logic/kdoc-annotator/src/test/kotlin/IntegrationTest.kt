/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationTest {
    @Test
    fun `annotate KDocs in LockFreeLinkedList`(@TempDir temporaryDirectory: Path) {
        val raw = Path("./src/test/test-data/LockFreeLinkedList.raw.kt")
        val expected = Path("./src/test/test-data/LockFreeLinkedList.expected.txt").readText()
        val source = raw.copyTo(temporaryDirectory.resolve(raw.fileName))

        forEachKtFileInDirectory(temporaryDirectory) { ktFile, path ->
            annotatePublicApiKDocs(ktFile, path, "https://ktor.io/feedback/")
        }

        assertEquals(expected, source.readText())
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
