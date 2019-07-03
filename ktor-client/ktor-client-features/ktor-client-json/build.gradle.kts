description = "Ktor client JSON support"

val ideaActive: Boolean by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization"))
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-gson"))

                runtimeOnly(project(":ktor-client:ktor-client-apache"))
                runtimeOnly(project(":ktor-client:ktor-client-cio"))
                runtimeOnly(project(":ktor-client:ktor-client-android"))
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
//                runtimeOnly(project(":ktor-client:ktor-client-jetty"))
            }
        }
        jsTest {
            dependencies {
                api(project(":ktor-client:ktor-client-js"))
            }
        }

//        if (!ideaActive) {
//            configure(listOf(getByName("macosX64Test"), getByName("iosX64Test"))) {
//                dependencies {
//                    implementation(project(":ktor-client:ktor-client-ios"))
//                }
//            }
//            configure(listOf(getByName("linuxX64Test"), getByName("macosX64Test"), getByName("mingwX64Test"))) {
//                dependencies {
//                    implementation(project(":ktor-client:ktor-client-curl"))
//                }
//            }
//        }
    }
}
