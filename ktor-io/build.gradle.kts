kotlin {
    createCInterop("mutex", posixTargets()) { _ ->
        defFile = File(projectDir, "posix/interop/mutex.def")
    }

    sourceSets {
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
