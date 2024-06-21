kotlin {
    createCInterop("threadUtils", nixTargets()) { _ ->
        defFile = File(projectDir, "nix/interop/threadUtils.def")
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
