import org.jetbrains.kotlin.gradle.plugin.*

description = "Ktor http client"

val ideaActive: Boolean by project

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http:ktor-http-cio"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network"))
        }
    }

    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-okhttp"))
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-features:ktor-websockets"))
        }
    }


    if (!ideaActive) {
        listOf("macosX64Test", "linuxX64Test", "mingwX64Test").map { named<KotlinSourceSet>(it) }.forEach {
            it {
                dependencies {
//                    api(project(":ktor-client:ktor-client-curl"))
                }
            }
        }

        listOf("macosX64Test", "iosX64Test", "iosArm32Test", "iosArm64Test").map { named<KotlinSourceSet>(it) }.forEach {
            it {
                dependencies {
//                    api(project(":ktor-client:ktor-client-ios"))
                }
            }
        }
    }
}
