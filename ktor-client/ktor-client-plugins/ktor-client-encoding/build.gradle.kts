/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

useJdkVersionForJvmTests(11)

apply<test.server.TestServerPlugin>()

kotlin.sourceSets {
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }
}
