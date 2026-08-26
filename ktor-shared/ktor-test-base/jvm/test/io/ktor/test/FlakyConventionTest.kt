/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import io.ktor.test.constants.FLAKY_NAME_TOKEN
import kotlin.test.Test

/** Simple name of [Flaky], matched textually — see [isFlaky]. */
private const val FLAKY_ANNOTATION = "Flaky"

/**
 * Enforces that the two flaky-test selectors agree wherever both apply.
 *
 * `@Flaky` is acted on by `FlakyTestCondition`, which needs a JUnit Platform and so exists only on
 * the JVM; the [FLAKY_NAME_TOKEN] suffix is filtered by Gradle on every target. A test compiled for
 * any non-JVM target therefore needs both, and one without the other silently does the wrong thing:
 * an annotation alone leaves the test running in the default Native/JS/Wasm suites, and a name token
 * alone leaves it running in the default JVM suite.
 *
 * Only `jvm/test` is exempt, because nothing there is compiled for another target. Everything else —
 * `common/test`, `jvmAndPosix/test`, `android/test` and the rest — is checked. Android host tests
 * are `Test` tasks that never receive the condition, so they need the token as well.
 */
class FlakyConventionTest {

    @Test
    fun `Flaky annotation and name token agree outside jvm-only test sources`() {
        val functions = nonJvmTestFunctions()

        functions
            .filter { it.isFlaky() }
            .assertTrue(
                additionalMessage = "A @Flaky test compiled for a non-JVM target also needs the " +
                    "'$FLAKY_NAME_TOKEN' name token, which is the only selector those targets have.",
            ) { it.name.endsWith(FLAKY_NAME_TOKEN) }

        functions
            .filter { it.name.endsWith(FLAKY_NAME_TOKEN) }
            .assertTrue(
                additionalMessage = "A test named '*$FLAKY_NAME_TOKEN' also needs @Flaky, which is " +
                    "what keeps it out of the default JVM run and records the tracking ticket.",
            ) { it.isFlaky() }
    }

    /**
     * Test functions from every test source set except the JVM-only one.
     *
     * The repository uses a flattened, platform-centric source layout (`<module>/<platform>/test`),
     * so source sets are selected by path rather than by Konsist's `scopeFromSourceSet`, which
     * expects the conventional `src/<name>` directories.
     */
    private fun nonJvmTestFunctions(): List<KoFunctionDeclaration> = Konsist
        .scopeFromProject()
        .files
        .filter { "/test/" in it.path && "/jvm/test/" !in it.path }
        .flatMap { it.functions(includeNested = true) }
}

/**
 * Whether the function carries [Flaky], matched by simple name.
 *
 * Konsist's `hasAnnotationOf` resolves the annotation through the file's imports, so it misses uses
 * that need no import — such as the tests in this module, which share [Flaky]'s package.
 */
private fun KoFunctionDeclaration.isFlaky(): Boolean = annotations.any { it.name == FLAKY_ANNOTATION }
