import org.jetbrains.kotlin.gradle.plugin.mpp.*

val nativeCompilations: List<KotlinNativeCompilation> by project.extra

kotlin {
    configure(nativeCompilations) {
        cinterops {
            val utils by creating {
                defFile("posix/interop/utils.def")
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
