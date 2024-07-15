
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
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
