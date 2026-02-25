/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {

    jvmToolchain(17)
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerAuth)
            api(libs.kotlinx.serialization.core)

            api(libs.opensaml.core.api)
            api(libs.opensaml.saml.api)
            api(libs.opensaml.security.api)
            api(libs.opensaml.xmlsec.api)
            api(libs.opensaml.messaging.api)

            implementation(libs.opensaml.saml.impl)
            implementation(libs.opensaml.xmlsec.impl)

            runtimeOnly(libs.opensaml.core.impl)
            runtimeOnly(libs.opensaml.security.impl)
            runtimeOnly(libs.opensaml.messaging.impl)
        }

        jvmTest.dependencies {
            implementation(projects.ktorNetworkTlsCertificates)
        }
    }
}
