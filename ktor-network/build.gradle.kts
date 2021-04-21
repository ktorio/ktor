import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

description = "Ktor network utilities"

val ideaActive: Boolean by project.extra
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
        commonMain {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        jvmTest {
            dependencies {
                implementation("io.mockk:mockk:$mockk_version")
            }
        }

        if (!ideaActive && findByName("posixMain") != null) {
            val networkInterop by creating
            getByName("posixMain").dependsOn(networkInterop)
            apply(from = "$rootDir/gradle/interop-as-source-set-klib.gradle")
            (project.ext.get("registerInteropAsSourceSetOutput") as groovy.lang.Closure<*>).invoke(
                "network",
                networkInterop
            )
        }
    }
}
