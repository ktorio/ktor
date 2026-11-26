/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// On CI, repositories are redirected by an init script installed into the Gradle user home (see ktor-build).
// The wrapper task is configured here, so the generated gradle-wrapper.properties always uses cache-redirector.
gradle.rootProject {
    gradle.taskGraph.whenReady {
        tasks.named<Wrapper>("wrapper") {
            distributionUrl = distributionUrl.replace(
                "https://services.gradle.org/distributions",
                "https://cache-redirector.jetbrains.com/services.gradle.org/distributions",
            )
        }
    }
}
