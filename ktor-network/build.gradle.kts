import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

description = "Ktor network utilities"

val nativeCompilations: List<KotlinNativeCompilation> by project.extra
val mockk_version: String by project.extra

kotlin {
    nativeCompilations.forEach {
        it.cinterops {
            val network by creating {
                defFile = projectDir.resolve("posix/interop/network.def")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.mockk:mockk:$mockk_version")
            }
        }

        if (findByName("posixMain") != null) {
            val networkInterop by creating

            val posixMain by getting {
                dependsOn(networkInterop)
            }

            val darwin by creating {
                listOf(
                    "macosX64",
                    "iosX64",
                    "iosArm64",
                    "iosArm32",
                    "tvosArm64",
                    "tvosX64",
                    "watchosArm32",
                    "watchosArm64",
                    "watchosX86",
                    "watchosX64",
                ).map { getByName("${it}Main") }.forEach { it.dependsOn(this) }
                dependsOn(posixMain)
            }

        }
    }
}
