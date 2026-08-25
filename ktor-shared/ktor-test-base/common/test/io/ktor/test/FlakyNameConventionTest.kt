/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlin.test.Test

/**
 * Fixture for the cross-platform `_flaky` name convention (see [Flaky]).
 *
 * Unlike the `@Flaky` annotation, which only JUnit can act on, the name token is filtered by Gradle
 * on every target. This test therefore has no body: its purpose is to be *selected*, so that the
 * filtering can be verified on Native/JS/Wasm as well as JVM.
 *
 * It must not run in a default test run, and must be the only test selected by
 * `-Pktor.tests.flaky=only`.
 */
class FlakyNameConventionTest {

    @Flaky("KTOR-9796")
    @Test
    fun nameTokenIsFilteredOnEveryTarget_flaky() = Unit
}
