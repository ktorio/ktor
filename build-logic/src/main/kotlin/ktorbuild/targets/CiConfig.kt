/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import com.gradle.develocity.agent.gradle.test.TestRetryConfiguration
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

/** Applies CI-specific configurations to test tasks. */
internal fun Project.configureTestTasksOnCi() {
    // Don't fail build on the CI:
    // 1. To distinct builds failed because of failed tests and because of compilation errors or anything else.
    //    TeamCity parses test results to define build status, so the build won't be green.
    // 2. To run as many tests as possible while keeping fail-fast behavior locally.
    tasks.withType<AbstractTestTask>().configureEach {
        ignoreFailures = true
        if (this is KotlinTest) ignoreRunFailures = true
    }
    // KotlinTestReport overwrites ignoreFailure values and fails build on test failure if this flag is disabled
    extra["kotlin.tests.individualTaskReports"] = true

    tasks.withType<KotlinJvmTest>().configureEach {
        testRetry {
            maxRetries = 1
            maxFailures = 10
        }
    }
}

// Docs: https://docs.gradle.com/develocity/gradle-plugin/current/#test_retry
private fun Test.testRetry(configure: TestRetryConfiguration.() -> Unit) {
    extensions.getByName<DevelocityTestConfiguration>("develocity").testRetry(configure)
}
