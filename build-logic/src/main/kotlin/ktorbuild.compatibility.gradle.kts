/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

kotlinExtension.abiValidation {
    enabled = true
}
