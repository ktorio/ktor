/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test.junit

import io.ktor.test.Flaky
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
    // Mirrors FlakyTestCondition: the property is always set by the build, so merely being
    // present says nothing — it has to say that flaky tests are enabled.
    val property: String? = System.getProperty("enable.flaky.tests")
    assertTrue(
        property !in setOf(null, "false", "0"),
        "This test is annotated @Flaky, so FlakyTestCondition must have disabled it, " +
            "but it ran with enable.flaky.tests=$property. Is the Extension service file " +
            "in jvm/resources/META-INF/services missing?",
    )
}

@Flaky(SELF_TEST_TICKET)
abstract class FlakyBaseClassFixture {
    @Test
    fun `class-level Flaky is inherited by subclasses`() = assertDisabledUnlessFlakyEnabled()
}

/** Kotlin annotations aren't `@Inherited`, so this case needs JUnit's annotation lookup. */
class FlakyInheritedClassTest : FlakyBaseClassFixture()

class FlakyAnnotatedMethodTest {
    @Flaky(SELF_TEST_TICKET)
    @Test
    fun `method-level Flaky is honored`() = assertDisabledUnlessFlakyEnabled()
}
