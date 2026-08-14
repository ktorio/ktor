/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Marks a test (method or class) as flaky (non-deterministic).
 *
 * Flaky tests are **excluded from the default test run** and executed only by the
 * dedicated flaky/nightly task (`enable.flaky.tests` — see build-logic `JvmConfig`),
 * so they stay tracked in Develocity instead of being deleted or silently retried.
 *
 * On JVM this is enforced by `io.ktor.test.junit.FlakyTestCondition` (auto-registered via JUnit
 * extension autodetection). There is no JUnit Platform on Native/JS/Wasm, so annotations can't be
 * used for selection there. **For a test in common code, also add the `_flaky` token to its name**,
 * which the build filters out on every target:
 *
 * ```
 * @Flaky("KTOR-1234")
 * @Test
 * fun testReconnectsAfterClose_flaky() { ... }
 * ```
 *
 * The token may sit on the test or on its class. Selection modes, applied to every target:
 *
 * | Command                                    | Runs                        |
 * |--------------------------------------------|-----------------------------|
 * | `./gradlew :module:jvmTest`                | everything except flaky     |
 * | `./gradlew :module:jsNodeTest -Pktor.tests.flaky=only` | flaky only      |
 * | `./gradlew :module:macosArm64Test -Pktor.tests.flaky=all` | everything   |
 *
 * The JVM-only `flakyTest` task runs the full suite with `@Flaky` tests enabled, since the
 * annotation is resolved at execution time rather than during selection.
 *
 * Prefer fixing flakiness over marking; when marking, always link the tracking issue.
 *
 * @property ticket The YouTrack issue tracking the flakiness, e.g. `"KTOR-1234"`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Flaky(val ticket: String)
