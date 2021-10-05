import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin {
    targets.apply {
        val current = mutableListOf<KotlinTarget>()
        if (IDEA_ACTIVE) {
            current.add(getByName("desktop"))
        } else {
            current.addAll(listOf(getByName("macosX64"), getByName("linuxX64"), getByName("mingwX64")))
        }

        val paths = listOf(
            "C:/msys64/mingw64/include/curl",
            "C:/Tools/msys64/mingw64/include/curl",
            "C:/Tools/msys2/mingw64/include/curl"
        )
        current.filterIsInstance<KotlinNativeTarget>().forEach { platform ->
            platform.compilations.getByName("main") {
                val libcurl by cinterops.creating {
                    defFile = File(projectDir, "desktop/interop/libcurl.def")

                    if (platform.name == "mingwX64") {
                        includeDirs.headerFilterOnly(paths)
                    } else {
                        includeDirs.headerFilterOnly(
                            listOf(
                                "/opt/homebrew/opt/curl/include/curl",
                                "/opt/local/include/curl",
                                "/usr/local/include/curl",
                                "/usr/include/curl",
                                "/usr/local/opt/curl/include/curl",
                                "/usr/include/x86_64-linux-gnu/curl",
                                "/usr/local/Cellar/curl/7.62.0/include/curl",
                                "/usr/local/Cellar/curl/7.63.0/include/curl",
                                "/usr/local/Cellar/curl/7.65.3/include/curl",
                                "/usr/local/Cellar/curl/7.66.0/include/curl"
                            )
                        )
                    }

                    afterEvaluate {
                        if (platform.name == "mingwX64") {
                            val winTests = tasks.getByName("mingwX64Test") as KotlinNativeTest
                            winTests.environment(
                                "PATH",
                                "c:\\msys64\\mingw64\\bin;c:\\tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin"
                            )
                        }
                    }
                }
            }
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        val desktopTest by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}
