/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import ktorbuild.*
import ktorbuild.internal.*
import ktorbuild.internal.publish.*

plugins {
    id("com.vanniktech.maven.publish")
    id("signing") apply false
}

addProjectTag(ProjectTag.Published)

mavenPublishing {
    if (shouldPublishToMavenCentral()) publishToMavenCentral(automaticRelease = true)
    configureSigning(this)

    pom {
        name = project.name
        description = project.description.orEmpty()
            .ifEmpty { "Ktor is a framework for quickly creating web applications in Kotlin with minimal effort." }
        url = "https://github.com/ktorio/ktor"
        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "JetBrains"
                name = "Jetbrains Team"
                organization = "JetBrains"
                organizationUrl = "https://www.jetbrains.com"
            }
        }
        scm {
            url = "https://github.com/ktorio/ktor.git"
        }
    }
}

publishing {
    repositories {
        addTargetRepositoryIfConfigured()
        mavenLocal()
    }
}

registerCommonPublishTask()

plugins.withId("ktorbuild.kmp") {
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val os = ktorBuild.os.get()
        // Workaround for https://github.com/gradle/gradle/issues/22641
        val predicate = provider { isAvailableForPublication(publication.name, os) }
        onlyIf("Available for publication on $os") { predicate.get() }
    }

    registerTargetsPublishTasks(ktorBuild.targets)
}

private fun Project.configureSigning(mavenPublishing: MavenPublishBaseExtension) {
    extra["signing.gnupg.keyName"] = (System.getenv("SIGN_KEY_ID") ?: return)
    extra["signing.gnupg.passphrase"] = (System.getenv("SIGN_KEY_PASSPHRASE") ?: return)

    mavenPublishing.signAllPublications()
    signing.useGpgCmd()

    // Workaround for https://github.com/gradle/gradle/issues/12167
    tasks.withType<Sign>().configureEach {
        withLimitedParallelism("gpg-agent", maxParallelTasks = 1)
    }
}
