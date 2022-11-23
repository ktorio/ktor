import org.jetbrains.kotlin.gradle.targets.native.tasks.*

apply<test.server.TestServerPlugin>()

val WIN_LIBRARY_PATH =
    "c:\\msys64\\mingw64\\bin;c:\\tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin"

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

plugins {
    id("kotlinx-serialization")
}

kotlin {
    if (fastTarget()) return@kotlin

    createCInterop("libcurl", listOf("macosX64", "linuxX64", "mingwX64")) {
        defFile = File(projectDir, "desktop/interop/libcurl.def")
        includeDirs.headerFilterOnly(paths)
    }

    createCInterop("libcurl", listOf("macosArm64")) {
        defFile = File(projectDir, "desktop/interop/libcurl_arm64.def")
        includeDirs.headerFilterOnly(paths)
    }

    sourceSets {
        desktopMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        desktopTest {
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
