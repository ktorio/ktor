apply<test.server.TestServerPlugin>()

val paths = listOf(
    "/opt/homebrew/opt/curl/include/",
    "/opt/local/include/",
    "/usr/local/include/",
    "/usr/include/",
    "/usr/local/opt/curl/include/",
    "/usr/include/x86_64-linux-gnu/",
    "/usr/local/Cellar/curl/*/include/",
    "desktop/interop/mingwX64/include/",
)

plugins {
    id("kotlinx-serialization")
}

kotlin {
    createCInterop("libcurl", listOf("macosX64", "linuxX64", "mingwX64")) {
        definitionFile = File(projectDir, "desktop/interop/libcurl.def")
        includeDirs.headerFilterOnly(paths)
    }

    createCInterop("libcurl", listOf("macosArm64")) {
        definitionFile = File(projectDir, "desktop/interop/libcurl_arm64.def")
        includeDirs.headerFilterOnly(paths)
    }

    createCInterop("libcurl", listOf("linuxArm64")) {
        definitionFile = File(projectDir, "desktop/interop/libcurl_linux_arm64.def")
        includeDirs.headerFilterOnly(listOf("desktop/interop/linuxArm64/include/"))
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
}
