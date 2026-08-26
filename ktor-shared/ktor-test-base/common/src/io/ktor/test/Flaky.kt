/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Marks a test (method or class) as flaky (non-deterministic).
 *
 * Flaky tests are **excluded from the default test run** and executed only by the dedicated
 * flaky/nightly task, so they stay tracked in Develocity instead of being deleted or silently
 * retried.
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
 * Both selectors are driven by one property, `ktor.tests.flaky`, applied to every target:
 *
 * | Command                                                   | Runs                    |
 * |-----------------------------------------------------------|-------------------------|
 * | `./gradlew :module:jvmTest`                               | everything except flaky |
 * | `./gradlew :module:jsNodeTest -Pktor.tests.flaky=only`    | flaky only              |
 * | `./gradlew :module:macosArm64Test -Pktor.tests.flaky=all` | everything              |
 *
 * The JVM-only `flakyTest` task runs **only** the `@Flaky` tests: it pins the same property to
 * `only` for the test JVM, so `FlakyTestCondition` skips everything else. It exists because the
 * annotation is resolved at execution time rather than during selection, which is what lets it
 * catch JVM tests that carry no `_flaky` token.
 *
 * ### Flakiness confined to one engine
 *
 * This annotation quarantines a whole test method. Both selectors act on the method — the JUnit
 * condition before the body runs, the name token during Gradle's test selection — so neither can see
 * which engine `clientTests` would have picked. Annotating a `clientTests` method that only flips on
 * one engine therefore drops the coverage for every *other* engine and every other target too.
 *
 * Until `ClientLoader` can express the scope directly, split the test and quarantine only the
 * affected engine:
 *
 * ```
 * @Test
 * fun testEcho() = clientTests(except("WinHttp")) { echo() }
 *
 * @Flaky("KTOR-1234")
 * @Test
 * fun testEcho_flaky() = clientTests(only("WinHttp")) { echo() }
 *
 * private fun TestClientBuilder<*>.echo() { test { client -> /* ... */ } }
 * ```
 *
 * Use `platform:Engine` patterns (`only("jvm:CIO")`) when the flakiness is specific to one platform's
 * build of an engine, so the others keep running.
 *
 * Prefer fixing flakiness over marking; when marking, always link the tracking issue.
 *
 * @property ticket The YouTrack issue tracking the flakiness, e.g. `"KTOR-1234"`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
// Inherited so that a test class extending an annotated base class is quarantined too — the shared
// test suite pattern used across Ktor. JUnit walks superclasses only for inherited annotations.
@JvmInherited
annotation class Flaky(val ticket: String)
