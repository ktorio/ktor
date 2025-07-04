/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UnstableApiUsage")

import ktorbuild.internal.*
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import java.time.Year

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/ktorio/ktor/blob/$version")
        }
    }

    pluginsConfiguration {
        html {
            customAssets.from(layout.settingsDirectory.file("ktor-dokka/assets/logo-icon.svg"))
            footerMessage = "© ${Year.now()} JetBrains s.r.o and contributors. Apache License 2.0"
        }
    }
}

tasks.withType<DokkaGeneratePublicationTask>().configureEach {
    // Reduce memory consumption on CI
    if (ktorBuild.isCI.get()) {
        withLimitedParallelism("dokka", maxParallelTasks = 2)
    }
}
