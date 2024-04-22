description = "Ktor network utilities"

kotlin {
    createCInterop("network", nixTargets()) {
        defFile = projectDir.resolve("nix/interop/network.def")
    }
    createCInterop("un", iosTargets() + tvosTargets() + watchosTargets()) {
        defFile = projectDir.resolve("nix/interop/un.def")
    }

    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        jvmAndNixTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-junit"))
                implementation(libs.mockk)
            }
        }
    }
}
