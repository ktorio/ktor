/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("binary-compatibility-validator")
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

// Bridge for the new task name used on CI
tasks.register("checkLegacyAbi") {
    dependsOn("apiCheck")
}
