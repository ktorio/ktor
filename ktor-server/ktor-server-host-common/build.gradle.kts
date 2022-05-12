description = ""

kotlin {
    nixTargets().forEach {
        it.compilations.getByName("main").cinterops {
            create("host_common") {
                defFile = projectDir.resolve("nix/interop/host_common.def")
            }
        }
    }

    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-http:ktor-http-cio"))
                api(project(":ktor-shared:ktor-websockets"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-server:ktor-server-test-host"))
                implementation(project(":ktor-server:ktor-server-test-suites"))
                implementation(project(":ktor-server:ktor-server-config-yaml"))
                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
                api(libs.logback.classic)
            }
        }
    }
}
