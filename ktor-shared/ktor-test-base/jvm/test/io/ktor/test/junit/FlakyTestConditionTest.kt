/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
import io.ktor.test.constants.FLAKY_MODE_PROPERTY
import io.ktor.test.constants.FlakyTestsMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ticket used by the fixtures below. These tests aren't actually flaky: they exist to verify that
 * [FlakyTestCondition] disables `@Flaky` tests, so they need the annotation to be under test.
 */
private const val SELF_TEST_TICKET = "KTOR-9796"

/**
 * Asserts that this test runs only when flaky tests are enabled.
 *
 * Each fixture below is annotated `@Flaky`, so [FlakyTestCondition] must disable it in the default
 * `jvmTest` run and enable it in `flakyTest`. Asserting the property is set therefore succeeds in
 * `flakyTest` and, should the condition ever stop recognizing the annotation, fails in `jvmTest`
 * with the message below instead of passing silently.
 */
private fun assertDisabledUnlessFlakyEnabled() {
    // Mirrors FlakyTestCondition: a @Flaky test runs only in `only` mode (the flakyTest task) or
    // `all` mode (the full suite, flaky included).
    val mode = FlakyTestsMode.of(System.getProperty(FLAKY_MODE_PROPERTY))
    assertTrue(
        mode != FlakyTestsMode.EXCLUDE,
        "This test is annotated @Flaky, so FlakyTestCondition must have disabled it, but it ran " +
            "with $FLAKY_MODE_PROPERTY=${System.getProperty(FLAKY_MODE_PROPERTY)}. Is the Extension " +
            "service file in jvm/resources/META-INF/services missing?",
    )
}

@Flaky(SELF_TEST_TICKET)
abstract class FlakyBaseClassFixture {
    @Test
    fun `class-level Flaky is inherited by subclasses`() = assertDisabledUnlessFlakyEnabled()
}

/** Covers `@Flaky` being meta-annotated `@JvmInherited`, which is what makes JUnit look here. */
class FlakyInheritedClassTest : FlakyBaseClassFixture()

class FlakyAnnotatedMethodTest {
    @Flaky(SELF_TEST_TICKET)
    @Test
    fun `method-level Flaky is honored`() = assertDisabledUnlessFlakyEnabled()
}
