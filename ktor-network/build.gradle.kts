description = "Ktor network utilities"

val mockk_version: String by project.extra

kotlin {
    nixTargets().forEach {
        it.compilations.getByName("main").cinterops {
            val network by creating {
                defFile = projectDir.resolve("nix/interop/network.def")
            }
        }
    }

    sourceSets {
        val jvmAndNixMain by getting {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        val jvmAndNixTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.mockk:mockk:$mockk_version")
            }
        }
    }
}
