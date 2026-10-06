/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.server-plugin")
}

val generateTestWebJarResources = tasks.register("generateTestWebJarResources") {
    val resourcesDirectory = layout.buildDirectory.dir("generated/test-webjar")
    outputs.dir(resourcesDirectory)

    doLast {
        val script = resourcesDirectory.get()
            .file("META-INF/resources/webjars/test-library/1.0.0/sample.js").asFile
        script.parentFile.mkdirs()
        script.writeText("console.log('test fixture');")
    }
}

val testWebJar = tasks.register<Jar>("testWebJar") {
    archiveFileName.set("test-webjar.jar")
    destinationDirectory.set(layout.buildDirectory.dir("test-webjar"))
    from(generateTestWebJarResources)
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.webjars.locator)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerConditionalHeaders)
            implementation(projects.ktorServerCachingHeaders)
            runtimeOnly(files(testWebJar))
        }
    }
}
