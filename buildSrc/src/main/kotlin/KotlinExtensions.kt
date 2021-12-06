import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun Project.kotlin(block: KotlinMultiplatformExtension.() -> Unit) {
    configure(block)
}

val Project.kotlin: KotlinMultiplatformExtension get() = the()

fun Project.useJdkVersionForJvmTests(version: Int) {
    tasks.getByName("jvmTest").apply {
        check(this is Test)

        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(maxOf(version, 8)))
            }
        )
    }
}
