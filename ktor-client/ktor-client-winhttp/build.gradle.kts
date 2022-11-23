import org.jetbrains.kotlin.gradle.targets.native.tasks.*

apply<test.server.TestServerPlugin>()

val WIN_LIBRARY_PATH = "c:\\msys64\\mingw64\\bin;c:\\tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin"

plugins {
    id("kotlinx-serialization")
}

kotlin {
    if (fastTarget()) return@kotlin

    createCInterop("winhttp", windowsTargets()) {
        defFile = File(projectDir, "windows/interop/winhttp.def")
    }

    sourceSets {
        windowsMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        windowsTest {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }

    afterEvaluate {
        if (HOST_NAME != "windows") return@afterEvaluate
        val winTests = tasks.findByName("mingwX64Test") as? KotlinNativeTest? ?: return@afterEvaluate
        winTests.environment("PATH", WIN_LIBRARY_PATH)
    }
}
