/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalAbiValidation::class)

import ktorbuild.internal.abiValidation
import org.jetbrains.kotlin.gradle.dsl.abi.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

kotlinExtension.abiValidation {
    enabled = true
}

// The property 'enabled' is not a part of the AbiValidationVariantSpec, so we need this bridge-method to unify enabling
private val AbiValidationVariantSpec.enabled: Property<Boolean>
    get() = when (this) {
        is AbiValidationMultiplatformExtension -> this.enabled
        is AbiValidationExtension -> this.enabled
        else -> error("Unexpected type: ${this::class.qualifiedName}")
    }
