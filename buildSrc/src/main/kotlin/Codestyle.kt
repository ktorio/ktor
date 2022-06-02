/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.kotlin.dsl.*

fun Project.configureCodestyle() {
    apply(plugin = "org.jmailen.kotlinter")

    kotlinter.apply {
        ignoreFailures = true
        reporters = arrayOf("checkstyle", "plain")
        experimentalRules = false
        disabledRules = arrayOf(
            "no-wildcard-imports",
            "indent"
        )
    }
}
