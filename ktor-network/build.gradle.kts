description = "Ktor network utilities"

kotlin {
    createCInterop("network", nixTargets()) {
        definitionFile = projectDir.resolve("nix/interop/network.def")
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

        macosTest {
            val nixTest = getByName("nixTest")
            dependsOn(nixTest)
        }
        watchosTest {
            val nixTest = getByName("nixTest")
            dependsOn(nixTest)
        }
        tvosTest {
            val nixTest = getByName("nixTest")
            dependsOn(nixTest)
        }
        iosTest {
            val nixTest = getByName("nixTest")
            dependsOn(nixTest)
        }
    }
}
