kotlin {
    targets {
        nixTargets().forEach {
            it.compilations.getByName("main") {
                cinterops.create("threadUtils") {
                    defFile = File(projectDir, "nix/interop/threadUtils.def")
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-io"))
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
