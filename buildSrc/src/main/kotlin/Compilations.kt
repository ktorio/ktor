/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.tasks.*

fun KotlinCompilationTask<*>.configureCompilerOptions() {
    compilerOptions {
        progressiveMode.set(true)
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}
