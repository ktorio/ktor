
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.8.2"
}

kotlin {
    createCInterop("mutex", posixTargets()) {
        defFile = File(projectDir, "posix/interop/mutex.def")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.io.core)
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
