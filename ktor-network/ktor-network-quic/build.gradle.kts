/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-network"))
                api(project(":ktor-utils"))
            }
        }
    }

    /*
     * IP_DONTFRAGMENT was added in java 19, see https://bugs.openjdk.org/browse/JDK-8284890
     * it is required (if possible) for the QUIC implementation, https://www.rfc-editor.org/rfc/rfc9000.html#name-datagram-size
     */
    // todo requires gradle 7.6
//    jvmToolchain(19)
}
