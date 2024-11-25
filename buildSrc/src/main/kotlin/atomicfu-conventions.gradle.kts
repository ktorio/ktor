/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("org.jetbrains.kotlinx.atomicfu")
}

// Workaround for KT-71203. Can be removed after https://github.com/Kotlin/kotlinx-atomicfu/issues/431
atomicfu {
    transformJs = false
}
