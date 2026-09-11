/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
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
    fun `exclude configured paths from scan`(@TempDir temporaryDirectory: Path) {
        val excludedPathPatterns = listOf(
            "**/.gradle/**",
            "**/build/**",
            "build-logic/**",
            "build-settings-logic/**",
        )
        val includedSource = Path("ktor-client/ktor-client-core/common/src/Client.kt")
        val excludedSources = listOf(
            Path(".gradle/caches/Generated.kt"),
            Path("ktor-client/.gradle/caches/Generated.kt"),
            Path("build/generated/Generated.kt"),
            Path("ktor-client/build/generated/Generated.kt"),
            Path("build-logic/src/main/kotlin/KtorBuild.kt"),
            Path("build-settings-logic/src/main/kotlin/KtorSettings.kt"),
        )

        for (source in excludedSources + listOf(includedSource)) {
            temporaryDirectory.resolve(source).apply {
                parent.createDirectories()
                writeText("package example")
            }
        }

        val scannedSources = mutableSetOf<Path>()
        val pathExclusions = PathExclusions(excludedPathPatterns)
        forEachKtFileInDirectory(
            temporaryDirectory,
            shouldVisit = { it !in pathExclusions },
        ) { _, path ->
            scannedSources.add(temporaryDirectory.relativize(path))
        }

        assertEquals(setOf(includedSource), scannedSources)
    }

    @Test
    fun `continue searching after partial path pattern match`() {
        val pathExclusions = PathExclusions(listOf("**/generated/src/**"))

        assertTrue(Path("generated/cache/generated/src/Generated.kt") in pathExclusions)
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
