import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.targets.native.tasks.*

val ideaActive: Boolean by project.extra
val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin {
    targets {
        // Workaround: 1.3.60. Possible because of the new inference.
        (this as NamedDomainObjectCollection<KotlinTarget>)

        val current = mutableListOf<KotlinTarget>()
        if (ideaActive) {
            current.add(getByName("posix"))
        } else {
            current.addAll(listOf(getByName("macosX64"), getByName("linuxX64"), getByName("mingwX64")))
        }

        val paths = listOf("C:/msys64/mingw64/include/curl", "C:/Tools/msys64/mingw64/include/curl", "C:/Tools/msys2/mingw64/include/curl")
        current.filterIsInstance<KotlinNativeTarget>().forEach { platform ->
            platform.compilations.getByName("main") {
                val libcurl by cinterops.creating {
                    defFile = File(projectDir, "posix/interop/libcurl.def")

                    if (platform.name == "mingwX64") {
                        includeDirs.headerFilterOnly(paths)
                    } else {
                        includeDirs.headerFilterOnly(
                            listOf(
                                "/opt/local/include/curl",
                                "/usr/local/include/curl",
                                "/usr/include/curl",
                                "/usr/local/opt/curl/include/curl",
                                "/usr/include/x86_64-linux-gnu/curl",
                                "/usr/local/Cellar/curl/7.62.0/include/curl",
                                "/usr/local/Cellar/curl/7.63.0/include/curl",
                                "/usr/local/Cellar/curl/7.65.3/include/curl",
                                "/usr/local/Cellar/curl/7.66.0/include/curl"
                            )
                        )

                    }

                    afterEvaluate {
                        if (platform.name == "mingwX64") {
                            val winTests = tasks.getByName("mingwX64Test") as KotlinNativeTest
                            winTests.environment("PATH", "c:\\msys64\\mingw64\\bin;c:\\tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin")
                        }
                    }
                }

            }

        }
    }

    sourceSets {
        posixMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        posixTest {
            dependencies {
                api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            }
        }

        // Hack: register the Native interop klibs as outputs of Kotlin source sets:
        if (!ideaActive) {
            val libcurlInterop by creating
            getByName("posixMain").dependsOn(libcurlInterop)
            apply(from = "$rootDir/gradle/interop-as-source-set-klib.gradle")
            (project.ext.get("registerInteropAsSourceSetOutput") as groovy.lang.Closure<*>).invoke(
                "libcurl",
                libcurlInterop
            )
        }
    }
}
