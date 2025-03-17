/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.*

plugins {
    id("com.gradle.develocity")
    id("com.gradle.common-custom-user-data-gradle-plugin")
}

val isCIRun = providers.environmentVariable("TEAMCITY_VERSION").isPresent

develocity {
    val startParameter = gradle.startParameter
    val scanJournal = File(settingsDir, "scan-journal.log")

    // Should be in sync with settings.gradle.kts
    server = "https://ge.jetbrains.com"

    // Copy the value to the local variable for compatibility with configuration cache
    val isCIRun = isCIRun

    buildScan {
        uploadInBackground = !isCIRun

        // These properties should be specified in ~/.gradle/gradle.properties
        val overriddenUsername = providers.gradleProperty("ktor.develocity.username").orNull.orEmpty().trim()
        val overriddenHostname = providers.gradleProperty("ktor.develocity.hostname").orNull.orEmpty().trim()
        obfuscation {
            ipAddresses { listOf("0.0.0.0") }
            hostname { overriddenHostname.ifEmpty { "concealed" } }
            username { originalUserName ->
                when {
                    isCIRun -> "TeamCity"
                    overriddenUsername == "<default>" -> originalUserName
                    overriddenUsername.isNotEmpty() -> overriddenUsername

                    else -> buildString {
                        append(originalUserName.first())
                        append("***")
                        append(originalUserName.last())
                    }
                }
            }
        }

        capture {
            fileFingerprints = true
        }

        buildScanPublished {
            scanJournal.appendText("${Date()} — $buildScanUri — $startParameter\n")
        }

        val skipBuildScans = providers.gradleProperty("ktor.develocity.skipBuildScans")
            .orNull
            .toBoolean()

        publishing.onlyIf { !skipBuildScans }
    }
}

buildCache {
    if (isCIRun) {
        local {
            isEnabled = false
        }
    }

    remote(develocity.buildCache) {
        isPush = isCIRun
        isEnabled = true
    }
}
