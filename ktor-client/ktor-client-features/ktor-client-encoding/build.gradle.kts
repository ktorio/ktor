val ideaActive: Boolean by project.extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    commonTest {
        dependencies {
            implementation(project(":ktor-client:ktor-client-tests"))
        }
    }
    jvmTest {
        dependencies {
            implementation(project(":ktor-client:ktor-client-android"))
            implementation(project(":ktor-client:ktor-client-okhttp"))
            implementation(project(":ktor-client:ktor-client-cio"))
            implementation(project(":ktor-client:ktor-client-apache"))
//            implementation(project(":ktor-client:ktor-client-jetty"))
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


