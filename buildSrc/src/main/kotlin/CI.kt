/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.gradle.develocity.agent.gradle.test.*
import org.gradle.api.*
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.*

val CI = System.getenv("TEAMCITY_VERSION") != null

/**
 * Applies CI-specific configurations to test tasks.
 *
 * Don't fail build on the CI:
 * 1. To distinct builds failed because of failed tests and because of compilation errors or anything else.
 *    TeamCity parses test results to define build status, so the build won't be green.
 * 2. To run as many tests as possible while keeping fail-fast behavior locally.
 */
fun Project.configureTestTasksOnCi() {
    tasks.withType<AbstractTestTask>().configureEach {
        ignoreFailures = true
        if (this is KotlinTest) ignoreRunFailures = true

        (this as? Test)?.testRetry {
            maxRetries = 1
            maxFailures = 10
        }
    }
}

// Docs: https://docs.gradle.com/develocity/gradle-plugin/current/#test_retry
private fun Test.testRetry(configure: TestRetryConfiguration.() -> Unit) {
    extensions.getByName<DevelocityTestConfiguration>("develocity").testRetry(configure)
}
