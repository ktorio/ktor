/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import org.gradle.api.artifacts.dsl.DependencyConstraintHandler

fun DependencyConstraintHandler.commonsLang3() {
    add("jvmMainImplementation", "org.apache.commons:commons-lang3:3.18.0") {
        // https://github.com/advisories/GHSA-j288-q9x7-2f5v
        because("CVE-2025-48924: Vulnerable to Uncontrolled Recursion when processing long inputs")
    }
}

fun DependencyConstraintHandler.commonsBeanutils() {
    add("jvmMainImplementation", "commons-beanutils:commons-beanutils:1.11.0") {
        // https://github.com/advisories/GHSA-wxr5-93ph-8wr9
        because("CVE-2025-48734: Improper Access Control vulnerability")
    }
}
