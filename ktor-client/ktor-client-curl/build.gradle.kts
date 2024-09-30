apply<test.server.TestServerPlugin>()

val paths = listOf(
    "/opt/homebrew/opt/curl/include/",
    "/opt/local/include/",
    "/usr/local/include/",
    "/usr/include/",
    "/usr/local/opt/curl/include/",
    "/usr/include/x86_64-linux-gnu/",
    "/usr/local/Cellar/curl/7.62.0/include/",
    "/usr/local/Cellar/curl/7.63.0/include/",
    "/usr/local/Cellar/curl/7.65.3/include/",
    "/usr/local/Cellar/curl/7.66.0/include/",
    "/usr/local/Cellar/curl/7.80.0/include/",
    "/usr/local/Cellar/curl/7.80.0_1/include/",
    "/usr/local/Cellar/curl/7.81.0/include/",
    "desktop/interop/mingwX64/include/",
)

plugins {
    id("kotlinx-serialization")
}

kotlin {
    createCInterop("libcurl", listOf("linuxX64", "linuxArm64", "macosX64", "macosArm64", "mingwX64")) { name ->
        definitionFile = File(projectDir, "desktop/interop/$name/libcurl.def")
        includeDirs.headerFilterOnly("desktop/interop/$name/include/")
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
