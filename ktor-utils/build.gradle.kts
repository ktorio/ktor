import org.jetbrains.kotlin.gradle.plugin.mpp.*

val nativeCompilations: List<KotlinNativeCompilation> by project.extra
val ideaActive: Boolean by project.extra

kotlin {
    configure(nativeCompilations) {
        cinterops {
            val utils by creating {
                defFile("posix/interop/utils.def")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-io"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        // Hack: register the Native interop klibs as outputs of Kotlin source sets:
        if (!ideaActive && rootProject.ext.get("native_targets_enabled") as Boolean) {
            val utilsInterop by creating
            getByName("posixMain").dependsOn(utilsInterop)
            apply(from = "$rootDir/gradle/interop-as-source-set-klib.gradle")
            (project.ext.get("registerInteropAsSourceSetOutput") as groovy.lang.Closure<*>).invoke(
                "utils",
                utilsInterop
            )
        }
    }
}
