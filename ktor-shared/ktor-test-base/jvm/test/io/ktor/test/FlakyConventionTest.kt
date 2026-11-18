/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import io.ktor.test.constants.FLAKY_NAME_TOKEN
import kotlin.test.Test

private const val FLAKY_ANNOTATION = "Flaky"

private const val TEST_ANNOTATION = "Test"

private val FLAKY_TICKET_PATTERN = Regex("""[A-Z]+-\d+""")

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
        val functions = nonJvmTestFunctions().filter { it.isTest() }

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

    @Test
    fun `Flaky ticket references a tracked issue`() {
        val flakyUsages = Konsist.scopeFromProject()
            .files
            .flatMap { file ->
                file.classes(includeNested = true, includeLocal = true) +
                    file.functions(includeNested = true, includeLocal = true)
            }
            .flatMap { it.annotations }
            .filter { it.name == FLAKY_ANNOTATION }

        flakyUsages.assertTrue(
            additionalMessage = "@Flaky's ticket argument must be a YouTrack issue id like 'KTOR-1234'.",
        ) { annotation ->
            val ticket = annotation.arguments.firstOrNull()?.value ?: return@assertTrue false
            FLAKY_TICKET_PATTERN.matches(ticket)
        }
    }

    private fun nonJvmTestFunctions(): List<KoFunctionDeclaration> = Konsist
        .scopeFromProject()
        .files
        .filter { "/test/" in it.path && "/jvm/test/" !in it.path }
        .flatMap { it.functions(includeNested = true) }
}

private fun KoFunctionDeclaration.isFlaky(): Boolean = hasFlakyAnnotationInChain()

private fun KoFunctionDeclaration.isTest(): Boolean = annotations.any { it.name == TEST_ANNOTATION }

private tailrec fun KoBaseDeclaration.hasFlakyAnnotationInChain(): Boolean {
    if (this is KoFileDeclaration) return false
    if (this is KoAnnotationProvider && annotations.any { it.name == FLAKY_ANNOTATION }) return true
    val parent = (this as? KoContainingDeclarationProvider)?.containingDeclaration ?: return false
    return parent.hasFlakyAnnotationInChain()
}
