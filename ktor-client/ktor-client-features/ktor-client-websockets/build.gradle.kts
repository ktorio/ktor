/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
        }
    }
    jsTest {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-okhttp"))
        }
    }
}
