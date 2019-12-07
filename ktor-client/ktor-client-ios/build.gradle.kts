
val ideaActive: Boolean by project.extra
val serialization_version: String by project.extra

kotlin.sourceSets {
    if (!ideaActive) {
        listOf("watchosArm32Main", "watchosArm64Main", "watchosX86Main").map { getByName(it) }.forEach {
            it.kotlin.srcDir("watchos/src")
        }
        listOf("tvosArm64Main", "tvosX64Main", "iosArm32Main", "iosArm64Main", "iosX64Main", "macosX64Main").map { getByName(it) }.forEach {
            it.kotlin.srcDir("nonWatchos/src")
        }
    }

    darwinMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    darwinTest {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-native:$serialization_version")
        }
    }
}
