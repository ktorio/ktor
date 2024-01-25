import org.jetbrains.kotlin.gradle.plugin.mpp.*

apply<test.server.TestServerPlugin>()

plugins {
    id("kotlinx-serialization")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.named("main") {
            cinterops.create("libcurl") {
                definitionFile = file("desktop/interop/libcurl.def")
                includeDirs(file("desktop/interop/include"))
                extraOpts("-libraryPath", file("desktop/interop/lib/${target.name}"))
            }
        }
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
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
