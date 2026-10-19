/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
import io.ktor.test.constants.FLAKY_MODE_PROPERTY
import io.ktor.test.constants.FlakyTestsMode
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Asserts that this test runs only when flaky tests are enabled.
 *
 * Each fixture below is annotated `@Flaky`, so [FlakyTestCondition] must disable it in the default
 * `jvmTest` run and enable it in `flakyTest`. Asserting the property is set therefore succeeds in
 * `flakyTest` and, should the condition ever stop recognizing the annotation, fails in `jvmTest`
 * with the message below instead of passing silently.
 */
private fun assertDisabledUnlessFlakyEnabled() {
    val mode = FlakyTestsMode.of(System.getProperty(FLAKY_MODE_PROPERTY))
    assertTrue(
        mode != FlakyTestsMode.EXCLUDE,
        "This test is annotated @Flaky, so FlakyTestCondition must have disabled it, but it ran " +
            "with $FLAKY_MODE_PROPERTY=${System.getProperty(FLAKY_MODE_PROPERTY)}. Is the Extension " +
            "service file in jvm/resources/META-INF/services missing?",
    )
}

@Flaky("KTOR-9796")
abstract class FlakyBaseClassFixture {
    @Test
    fun `class-level Flaky is inherited by subclasses`() = assertDisabledUnlessFlakyEnabled()
}

/** Covers `@Flaky` being meta-annotated `@JvmInherited`, which is what makes JUnit look here. */
class FlakyInheritedClassTest : FlakyBaseClassFixture()

class FlakyAnnotatedMethodTest {
    @Flaky("KTOR-9796")
    @Test
    fun `method-level Flaky is honored`() = assertDisabledUnlessFlakyEnabled()
}

@Flaky("KTOR-9796")
class FlakyEnclosingClassTest {
    @Nested
    inner class NestedFixture {
        @Test
        fun `Flaky on the enclosing class is honored for nested tests`() = assertDisabledUnlessFlakyEnabled()
    }
}
