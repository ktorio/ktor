import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.targets.native.tasks.*

val WIN_LIBRARY_PATH =
    "c:\\msys64\\mingw64\\bin;c:\\tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin"

plugins {
    id("kotlinx-serialization")
}

kotlin {
    targets.apply {
        val current = listOf(
            getByName("macosX64"),
            getByName("linuxX64"),
            getByName("mingwX64")
        )

        val currentArm64 = listOf(
            getByName("macosArm64"),
        )

        val paths = if (HOST_NAME == "windows") {
            listOf(
                "C:/msys64/mingw64/include/curl",
                "C:/Tools/msys64/mingw64/include/curl",
                "C:/Tools/msys2/mingw64/include/curl"
            )
        } else {
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
                "/usr/local/Cellar/curl/7.66.0/include/curl",
                "/usr/local/Cellar/curl/7.80.0/include/curl",
                "/usr/local/Cellar/curl/7.80.0_1/include/curl",
                "/usr/local/Cellar/curl/7.81.0/include/curl"
            )
        }

        current.filterIsInstance<KotlinNativeTarget>().forEach { platform ->
            platform.compilations.getByName("main") {
                cinterops.create("libcurl") {
                    defFile = File(projectDir, "desktop/interop/libcurl.def")
                    includeDirs.headerFilterOnly(paths)

                    afterEvaluate {
                        if (platform.name == "mingwX64") {
                            val winTests = tasks.getByName("mingwX64Test") as KotlinNativeTest
                            winTests.environment("PATH", WIN_LIBRARY_PATH)
                        }
                    }
                }
            }
        }

        currentArm64.filterIsInstance<KotlinNativeTarget>().forEach { platform ->
            platform.compilations.getByName("main") {
                cinterops.create("libcurl") {
                    defFile = File(projectDir, "desktop/interop/libcurl_arm64.def")
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
