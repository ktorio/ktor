/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import org.jmailen.gradle.kotlinter.tasks.*

fun Project.configureCodestyle() {
    apply(plugin = "org.jmailen.kotlinter")

    kotlinter.apply {
        ignoreLintFailures = true
        reporters = arrayOf("checkstyle", "plain")
    }

    val editorconfigFile = rootProject.file(".editorconfig")
    tasks.withType<LintTask>().configureEach {
        inputs.file(editorconfigFile).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
